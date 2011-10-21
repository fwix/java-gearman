package org.gearman;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;


import org.gearman.core.GearmanConstants;
import org.gearman.core.GearmanPacket;
import org.gearman.util.ByteArray;
import org.gearman.util.EqualsLock;

import org.gearman.ServerJob.JobPriority;







class ServerFunctionMap {
	
	
	private final ConcurrentHashMap<ByteArray, Reference<InnerFunction>> funcMap = new ConcurrentHashMap<ByteArray, Reference<InnerFunction>>();
	private final EqualsLock lock = new EqualsLock();
	
	RoundRobin rrTracker = new RoundRobin();
	
	public void removeJobs(ByteArray function){
			
		if(this.funcMap.containsKey(function)){
			ServerFunction serverFunc = this.getFunction(function);
		
			serverFunc.removeJobs();
		}
		
		//this.funcMap.remove(function);
	
	}
	

	
	public int getQueueCount(){
		return funcMap.size();
		
	}
	
	public boolean grabJob(final ServerClient worker){
		
		return rrTracker.grabJob(worker);
		
	}
	
	
	
	public final ServerFunction getFunction(ByteArray name) {
		Integer key = name.hashCode();
		try {
			lock.lock(key);
			
			final Reference<InnerFunction> ref = funcMap.get(name);
			InnerFunction func;
			
			if(ref==null || (func=ref.get())==null) {
				func = new InnerFunction(name);
				final Reference<InnerFunction> ref2 = new SoftReference<InnerFunction>(func);
				func.ref = ref2;
				
				final Object o = this.funcMap.put(name, ref2);
				assert o==null;
			}
			return func;
		} finally {
			lock.unlock(key);
		}
	}
	
	public final ServerFunction getFunctionIfDefined(ByteArray name) {
		final Reference<InnerFunction> ref = funcMap.get(name);
		return ref==null? null: ref.get();
	}
	
	
	
	public final void sendStatusNoDone(ServerClient client) {
		
		for(Reference<InnerFunction> funcRef : funcMap.values()) {
			InnerFunction func = funcRef.get();
			if(func!=null) 
				client.sendPacket(func.getStatus(), null);
		}
		
	}
	public final void sendStatus(ServerClient client){
		
		sendStatusNoDone(client);
		
		client.sendPacket(ServerStaticPackets.TEXT_DONE, null /*TODO*/);
	}
	
	private final class InnerFunction extends ServerFunctionImpl {
		private Reference<?> ref;
		
		public InnerFunction(ByteArray name) {
			super(name);
		}
		
		@Override
		protected final void finalize() throws Throwable {
			super.finalize();
			ServerFunctionMap.this.funcMap.remove(super.getName(), ref);
		}
	}
	
	public void createJob(ByteArray funcNameBA, ByteArray uniqueIDBA,byte[] data,ServerJob.JobPriority priority,ServerClient client,boolean isBackground){
		
		
		ServerFunction func = getFunction(funcNameBA);
		func.createJob(uniqueIDBA, data, priority, client, isBackground);
		
		for(ByteArray funcName : funcMap.keySet()){
			
			ServerFunction serverFunc = this.getFunction(funcName);
			serverFunc.wakeUp();
			
		}
		
		
	}
	
	class ServerFunctionImpl implements ServerFunction {

		/** The function's name */
		private final ByteArray name;
		/** The lock preventing jobs with the same ID to be created or altered at the same time */
		private final EqualsLock lock = new EqualsLock();
		/** The set of jobs created by this function. ByteArray is equal to the uID */
		private Map<ByteArray,Job> jobSet = new ConcurrentHashMap<ByteArray,Job>();
		/** The queued jobs waiting to be processed */
		private ServerJobQueue<Job> queue = new ServerJobQueue<Job>();
		/** The list of workers waiting for jobs to be placed in the queue */
		private Set<ServerClient> workers = new CopyOnWriteArraySet<ServerClient>();
		/** The maximum number of jobs this function can have at any one time */
		private int maxQueueSize = 0;
		
		
		public void removeJobs(){
			
			
			while(this.queue.size() > 0){
				
				
				ServerJob job = ServerFunctionImpl.this.queue.poll();
				this.jobSet.remove(job.getUniqueID());
				//System.out.println("removing jobs : " + job.getUniqueID() + " " + Thread.currentThread());
			}
		}
			
		public ServerFunctionImpl(final ByteArray name) {
			this.name = name;
		}
		public final void addNoopable(final ServerClient noopable) {
			workers.add(noopable);
		}
		public final void removeNoopable(final ServerClient noopable) {
			workers.remove(noopable);
		}
		public final void setMaxQueue(final int size) {
			synchronized(this.jobSet) { this.maxQueueSize = size; }
		}
		
		public final ByteArray getName() {
			return this.name;
			//String funcName = new String("gm_task");
			//final ByteArray funcNameBA = new ByteArray(funcName.getBytes());
			//return funcNameBA;
		}
		
		public void wakeUp(){
			
			for(ServerClient noop : workers) {
				noop.noop();
			}
		}
		
		
		public final boolean queueIsEmpty() {
			return this.queue.isEmpty();
		}
		
		public String toString(){
			
			return name.toString();
			
		}
		
		public final GearmanPacket getStatus() {
				StringBuilder sb = new StringBuilder();
				sb.append(this.name.toString(GearmanConstants.UTF_8)); sb.append('\t');
				sb.append(this.jobSet.size()); sb.append('\t');
				sb.append(this.jobSet.size()-this.queue.size());sb.append('\t');
				sb.append(this.workers.size());sb.append('\n');
				
				return GearmanPacket.createTEXT(sb.toString());
		}
		
		public Integer getWorkingCount(){
			
			return this.jobSet.size() - this.queue.size();
		}
		
		public final void createJob(final ByteArray uniqueID, final byte[] data, final JobPriority priority, final ServerClient creator, boolean isBackground) {
			
			final Integer key = uniqueID.hashCode(); 
			this.lock.lock(key);
			try {
				
				// Make sure only one thread attempts to add a job with this uID at once
				
				if(this.jobSet.containsKey(uniqueID)) {
					
					final Job job = this.jobSet.get(uniqueID);
					assert job!=null;
					
					// If creator is specified, add creator to listener set and send JOB_CREATED packet
					if(!isBackground) {
						job.addClient(creator);
						creator.sendPacket(job.createJobCreatedPacket(), null /*TODO*/);
					}
					
					return;
				}
				
				final Job job;
				
				/* 
				 * Note: with this maxQueueSize variable not being synchronized, it is
				 * possible for a few threads to slip in and add jobs after the
				 * maxQueueSize variable is set, but I've decided that is it not
				 * worth the cost to guarantee this minute feature, especially since
				 * it's possible to have more then maxQueueSize jobs if the jobs were
				 * added prior to the variable being set.
				 */
				if(this.maxQueueSize>0) {
					synchronized (this.jobSet) {
						if(maxQueueSize>0 && maxQueueSize<=jobSet.size()) {
							creator.sendPacket(ServerStaticPackets.ERROR_QUEUE_FULL,null);
							return;
						}
						
						job = new Job(uniqueID, data, priority, isBackground?null:creator);
						this.jobSet.put(uniqueID, job);
					}
				} else {
					job = new Job(uniqueID, data, priority, isBackground?null:creator);	
					this.jobSet.put(uniqueID, job);		// add job to local job set
				}
				
				
				/* 
				 * The JOB_CREATED packet must sent before the job is added to the queue.
				 * Queuing the job before sending the packet may result in another thread
				 * grabbing, completing and sending a WORK_COMPLETE packet before the
				 * JOB_CREATED is sent 
				 */
				if(creator!=null) {
					creator.sendPacket(job.createJobCreatedPacket(), null /*TODO*/);
				}
				
				/*
				 * The job must be queued before sending the NOOP packets. Sending the noops
				 * first may result in a worker failing to grab the job 
				 */
				this.queue.add(job);
				
				rrTracker.track(name, 1);
				 
				
				for(ServerClient noop : workers) {
					noop.noop();
				}
				
			} finally {
				// Always unlock lock
				this.lock.unlock(key);
			}
		}
		
		public final boolean grabJob(final ServerClient worker) {
			
			final Job job = this.queue.poll();
			
			
			if(job==null) return false;
			
			rrTracker.track(name, -1);
			job.work(worker);
			return true;
		}
		
		public final boolean grabJobUniqueID(final ServerClient worker) {
			final Job job = this.queue.poll();
			if(job==null) return false;
			
			rrTracker.track(name, -1);
			job.workUniqueID(worker);
			return true;
		}
		
		private final class Job extends ServerJobAbstract {

			Job(ByteArray uniqueID, byte[] data, JobPriority priority, ServerClient creator) {
				super(uniqueID, data, priority, creator);
			}

			@Override
			protected final synchronized void onComplete(final JobState prevState) {
				assert prevState!=null;
				switch(prevState) {
				case QUEUED:
					// Remove from queue
					final boolean value = ServerFunctionImpl.this.queue.remove(this);
					ServerFunctionMap.this.rrTracker.track(ServerFunctionImpl.this.name, -1);
					assert value;
				case WORKING:
					final ServerJob job = ServerFunctionImpl.this.jobSet.remove(this.getUniqueID());
					assert job.equals(this);
					// Remove from jobSet
				case COMPLETE:
					// Do nothing
				}
			}

			@Override
			protected final synchronized void onQueue(final JobState prevState) {
				assert prevState!=null;
				switch(prevState) {
				case QUEUED:
					// Do nothing
					break;
				case WORKING:
					// Requeue
					assert !ServerFunctionImpl.this.queue.contains(this);
					final boolean value = ServerFunctionImpl.this.queue.add(this);
					ServerFunctionMap.this.rrTracker.track(ServerFunctionImpl.this.name, 1);
					assert value;
					break;
				case COMPLETE:
					assert false;
					// should never go from COMPLETE to QUEUED
					break;
				}
			}

			@Override
			public ServerFunction getFunction() {
				return ServerFunctionImpl.this;
			}
		}
	}
	
	public class RoundRobin{
		
		
		public Map<ByteArray,Integer> funcCount = new ConcurrentHashMap<ByteArray,Integer>();
		ByteArray[] funcNames = new ByteArray[0];
		int index = -1; // funcNames.length + 1;
		
		public synchronized ByteArray[] getNames(){
			
				Set<ByteArray> keySet = funcCount.keySet();
				int size = keySet.size();
				
				ByteArray[] names = (ByteArray[])keySet.toArray(new ByteArray[size]);
				
				return names;
			
			
		}
		
		public synchronized void reset(){
			
			funcNames = getNames();
			index = 0;

		}
		
		public synchronized boolean grabJob(ServerClient worker){
			
			//System.out.println("I am starting to grab job "+ worker.getClientId() +" " + System.currentTimeMillis()/1000);
			int totalServer = Main.getServerCount();
			int currentIndex = index;
			do{
				if(index == -1 || index >= funcNames.length){
					reset();
					currentIndex = funcNames.length;
				}
			
				if(funcNames.length == 0){
					return false;
				
				}
			
				
				
				ByteArray next = funcNames[index];
				index = index + 1;
			
				ServerFunction nextFunc = ServerFunctionMap.this.getFunction(next);
				Integer throttle = Main.getThrottle(next.toString());
				
				if(throttle != null && totalServer > 0){
					throttle = throttle/totalServer;
				}
				
			
			
				if(nextFunc == null){
					return false;
				
				}
			
				
				if(throttle == null || (throttle > 0 && nextFunc.getWorkingCount()< throttle)){ //TODO : throttle > 0
					if( nextFunc.grabJob(worker)){
						//System.out.println("I just grabbed a job "+ worker.getClientId() +" " +  + System.currentTimeMillis());
						return true;
					}
				}
			}while(index!=currentIndex);
			return false;
			

		}
		
		public synchronized void track(ByteArray name, int countdiff){
			
				Integer count = funcCount.get(name);
				
				if(count == null){
					count = 0;
				}
				
				count = count + countdiff;
				assert count >=0 ;
				
				if(count == 0){
					funcCount.remove(name);
					
				}
				else{
					funcCount.put(name, count);
				}
			
		}
		
		
		
		
	}

}
