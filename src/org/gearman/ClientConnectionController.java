package org.gearman;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.gearman.GearmanClient.SubmitCallbackResult;
import org.gearman.GearmanJob.Priority;
import org.gearman.JobServerPoolAbstract.ConnectionController;
import org.gearman.core.GearmanCallbackResult;
import org.gearman.core.GearmanConnection;
import org.gearman.core.GearmanPacket;
import org.gearman.core.GearmanConstants;
import org.gearman.util.ByteArray;

abstract class ClientConnectionController <K, C extends GearmanCallbackResult> extends ConnectionController<K, C> {
	
	private static final int RESPONCE_TIMEOUT = 19000;
	private static final int IDLE_TIMEOUT = 9000;
	
	private Queue<Runnable> multiplexTasks = new LinkedBlockingQueue<Runnable>();
	private boolean isMultiplexing = false;
	
	/**
	 * The set of executing jobs. The key is the job's handle and the value is the job itself
	 */
	private final ConcurrentHashMap<ByteArray, GearmanJob> jobs = new ConcurrentHashMap<ByteArray, GearmanJob>();
	private ClientJobSubmission pendingJob = null;
	
	private long responceTimeout = Long.MAX_VALUE;
	private long idleTimeout = Long.MAX_VALUE;
	
	ClientConnectionController(final ClientImpl client, final K key) {
		super(client, key);
	}
	
	public final void timeoutCheck(long time) {
		if(time-this.responceTimeout>RESPONCE_TIMEOUT) {
			super.timeout();
		} else if(this.jobs.isEmpty() && this.pendingJob==null && time-this.idleTimeout>IDLE_TIMEOUT) {
			this.closeServer();
		}
	}
	
	protected final void close() {
		if(this.pendingJob!=null) {
			this.requeueJob(pendingJob);
			this.pendingJob = null;
		}
		
		Iterator<GearmanJob> it = this.jobs.values().iterator();
		while(it.hasNext()) {
			GearmanJob job = it.next();
			it.remove();
			job.setResult(GearmanJobResult.DISCONNECT_FAIL);
		}
		
		this.responceTimeout = Long.MAX_VALUE;
		this.idleTimeout = Long.MAX_VALUE;
	}
	
	protected abstract ClientJobSubmission pollNextJob();
	protected abstract void requeueJob(ClientJobSubmission jobSub);
	protected abstract Gearman getGearman();
	
	protected final boolean grab() {
		
		final ClientJobSubmission jobSub;
		synchronized(this) {
			if(this.pendingJob!=null) return false;
			jobSub = this.pollNextJob();
			
			if(jobSub!=null) 
				this.pendingJob = jobSub;
			else
				return false;
		}
		
		GearmanJob job = jobSub.job;
		
		final Priority p = job.getJobPriority();
		final String funcName = job.getFunctionName();
		final byte[] uID = job.getUniqueID();
		final byte[] data = job.getJobData();
		
		if(jobSub.isBackground) {
			switch(p) {
			case LOW_PRIORITY:
				this.getConnection().sendPacket(GearmanPacket.createSUBMIT_JOB_LOW_BG(funcName, uID, data), null/*TODO*/);
				break;
			case HIGH_PRIORITY:
				this.getConnection().sendPacket(GearmanPacket.createSUBMIT_JOB_HIGH_BG(funcName, uID, data), null/*TODO*/);
				break;
			case NORMAL_PRIORITY:
				this.getConnection().sendPacket(GearmanPacket.createSUBMIT_JOB_BG(funcName, uID, data), null/*TODO*/);
				break;
			}
		} else {
		
			switch(p) {
			case LOW_PRIORITY:
				this.getConnection().sendPacket(GearmanPacket.createSUBMIT_JOB_LOW(funcName, uID, data), null/*TODO*/);
				break;
			case HIGH_PRIORITY:
				this.getConnection().sendPacket(GearmanPacket.createSUBMIT_JOB_HIGH(funcName, uID, data), null/*TODO*/);
				break;
			case NORMAL_PRIORITY:
				this.getConnection().sendPacket(GearmanPacket.createSUBMIT_JOB(funcName, uID, data), null/*TODO*/);
				break;
			}
		}
		
		return true;
	}
		
	@Override
	public void onPacketReceived(GearmanPacket packet, GearmanConnection<Object> conn) {
		switch (packet.getPacketType()) {
		case JOB_CREATED:
			jobCreated(packet);
			break;
		case WORK_STATUS:
			workStatus(packet);
			break;
		case WORK_COMPLETE:
			workComplete(packet);
			break;
		case WORK_FAIL:
			workFail(packet);
			break;
		case ECHO_RES:	// Not used
			assert false;
			break;
		case ERROR:
			error(packet);
			break;
		case STATUS_RES:
			super.onStatusReceived(packet);
			break;
		case WORK_EXCEPTION:
			//workException(packet);  //TODO Don't implement yet
			assert false;
			break;
		case OPTION_RES:
			//TODO
			break;
		case WORK_DATA:
			workData(packet);
			break;
		case WORK_WARNING:
			workWarning(packet);
			break;
		default: // Not a client response
			assert false;
		}
	}
	
	private final void workWarning(final GearmanPacket packet) {
		final ByteArray jobHandle = new ByteArray(packet.getArgumentData(0));
		final GearmanJob job = this.jobs.get(jobHandle);
		
		if(job==null) {
			// TODO log warning
			return;
		}
		
		final byte[] warning = packet.getArgumentData(1);
		Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					job.callbackWarning(warning);
				} catch (Throwable t) {
					// If the user throws an exception, catch it, print it, and continue.
					t.printStackTrace();
				} finally {
					taskNext();
				}
			}
		};
		
		taskAdd(task);
	}
	
	private final void workData(final GearmanPacket packet) {
		final ByteArray jobHandle = new ByteArray(packet.getArgumentData(0));
		final GearmanJob job = this.jobs.get(jobHandle);
		
		if(job==null) {
			// TODO log warning
			return;
		}
		
		final byte[] data= packet.getArgumentData(1);
		Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					job.callbackData(data);
				} catch (Throwable t) {
					// If the user throws an exception, catch it, print it, and continue.
					t.printStackTrace();
				} finally {
					taskNext();
				}
			}
		};
		taskAdd(task);
	}

/*
	private final void workException(final GearmanPacket packet) {
		final ByteArray jobHandle = new ByteArray(packet.getArgumentData(0));
		final GearmanJob job = this.jobs.get(jobHandle);
		
		if(job==null) {
			// TODO log warning
			return;
		}
		
		final byte[] exception = packet.getArgumentData(1);
		try {
			job.callbackException(exception);
		} catch (Throwable t) {
			// If the user throws an exception, catch it, print it, and continue.
			t.printStackTrace();
		}
	}
*/
	
	private final void workFail(final GearmanPacket packet) {
		final ByteArray jobHandle = new ByteArray(packet.getArgumentData(0));
		
		/*
		 * Note: synchronization is not needed here.
		 */
		final GearmanJob job = this.jobs.remove(jobHandle);
		if(this.jobs.isEmpty()) {
			this.idleTimeout = System.currentTimeMillis();
		}
		
		if(job==null) {
			// TODO log warning
			return;
		}
		
		Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					job.setResult(GearmanJobResult.WORKER_FAIL);
				} catch (Throwable t) {
					// If the user throws an exception, catch it, print it, and continue.
					t.printStackTrace();
				} finally {
					taskNext();
				}
			}
		};
		
		taskAdd(task);
	}
	
	private final void jobCreated(final GearmanPacket packet) {
		final ClientJobSubmission jobSub;
		
		synchronized(this.jobs) {
			assert this.pendingJob!=null;
			
			jobSub = this.pendingJob;
			assert jobSub!=null;
			assert jobSub.job!=null;
			
			this.pendingJob = null;
		}
		
		jobSub.onSubmissionComplete(SubmitCallbackResult.SUBMIT_SUCCESSFUL);
		
		if(!jobSub.isBackground) {
			final ByteArray jobHandle = new ByteArray(packet.getArgumentData(0));
			jobSub.job.setConnection(this, jobHandle);
			this.jobs.put(jobHandle, jobSub.job);
		}
		
		this.grab();
	}
	
	private final void workStatus(final GearmanPacket packet) {
		
		final ByteArray jobHandle = new ByteArray(packet.getArgumentData(0));
		final GearmanJob job = ClientConnectionController.this.jobs.get(jobHandle);
		
		if(job==null) {
			// TODO log warning
			return;
		}
		
		try {
			final long numerator = Long.parseLong(new String(packet.getArgumentData(1),GearmanConstants.UTF_8));
			final long denominator = Long.parseLong(new String(packet.getArgumentData(2),GearmanConstants.UTF_8));
			
			Runnable task = new Runnable() {

				@Override
				public void run() {
					try {
						job.callbackStatus(numerator, denominator);
					} catch (Throwable t) {
						// If the user throws an exception, catch it, print it, and continue.
						t.printStackTrace();
					} finally {
						taskNext();
					}
				}
				
			};
			
			taskAdd(task);
		} catch (NumberFormatException nfe) {
			// TODO log error
		}
	}
	
	private final void taskAdd(Runnable task) {
		synchronized(this.multiplexTasks) {
			if(this.isMultiplexing)
				this.multiplexTasks.add(task);
			else {
				this.isMultiplexing = true;
				ClientConnectionController.this.getGearman().getPool().execute(task);
			}
		}
	}
	
	private final void taskNext() {
		
		synchronized(this.multiplexTasks) {
			Runnable task = this.multiplexTasks.poll();
			if(task==null) {
				this.isMultiplexing = false;
				return;
			} else {
				ClientConnectionController.this.getGearman().getPool().execute(task);
			}
		}
	}
		
	private final void workComplete(final GearmanPacket packet) {
		final ByteArray jobHandle = new ByteArray(packet.getArgumentData(0));

		/*
		 * Note: synchronization is not needed here.
		 */
		final GearmanJob job = this.jobs.remove(jobHandle);
		if(this.jobs.isEmpty()) {
			this.idleTimeout = System.currentTimeMillis();
		}
		
		if(job==null) {
			//TODO log warning
			return;
		}
		
		final byte[] data = packet.getArgumentData(1);
		Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					job.setResult(GearmanJobResult.workSuccessful(data));
				} catch (Throwable t) {
					// If the user throws an exception, catch it, print it, and continue.
					t.printStackTrace();
				} finally {
					taskNext();
				} 
			}
			
		};
		taskAdd(task);
	}
	
	private final void error(final GearmanPacket packet) {
		//TODO log error
	}
}
