package org.gearman;

import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.gearman.core.GearmanCallbackHandler;
import org.gearman.core.GearmanConnection;
import org.gearman.core.GearmanPacket;
import org.gearman.core.GearmanConstants;
import org.gearman.core.GearmanConnection.SendCallbackResult;
import org.gearman.util.ByteArray;
import java.util.Date;

class ServerClientImpl implements ServerClient{

	private final GearmanConnection<?> conn;
	
	/** The set of all functions that this worker can perform */
	private final ConcurrentHashMap<ByteArray,ServerFunction> funcMap = new ConcurrentHashMap<ByteArray,ServerFunction>();
	/** The set of all disconnect listeners */
	private final Set<ServerClientDisconnectListener> disconnectListeners = new HashSet<ServerClientDisconnectListener>();
	/** Indicates if the client is to be notified when the next job comes in */ 
	private boolean isSleeping	 = false;
	/** The client id */
	private String clientID	 = "-";
	/** Indicates if exception packets should be forward to clients*/
	private boolean isForwardsExceptions = false;
	/** Indicates if this ServerClient is closed */
	private boolean isClosed = false;
	 Random generator = new Random();
	Date startTime;
	Date endTime;
	 
	
	public ServerClientImpl(final GearmanConnection<?> conn) {
		this.conn = conn;
		int i = generator.nextInt();
		if (i < 0){
			i = i * -1;
		}
		this.clientID = Integer.toString(i);
		
	}
	
	@SuppressWarnings("unused")
	private final void DBG_printListenersSize() {
		/* Debug Method */
		System.out.println(disconnectListeners.size());
	}
	
	@Override
	public boolean addDisconnectListener(ServerClientDisconnectListener listener) {
		assert listener!=null;
		
		synchronized(this) {
			if(this.isClosed) {
				listener.onDisconnect(this);
				return true;
			} else {
				synchronized(this.disconnectListeners) {
					return this.disconnectListeners.add(listener);
				}
			}
		}
	}

	@Override
	public boolean can_do(ServerFunction func) {
		assert func!=null;
		
		final boolean value = funcMap.putIfAbsent(func.getName(), func)==null;
		if(value) {
			func.addNoopable(this);
		}
		return value;
	}
	
	@Override
	public boolean cant_do(ByteArray funcName) {
		final ServerFunction value = funcMap.remove(funcName);
		value.removeNoopable(this);
		
		return value!=null;
	}
	
	@Override
	public synchronized void close() {
		if(this.isClosed) return;
		
		this.isClosed = true;
		
		try { this.conn.close(); }
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		synchronized(this.disconnectListeners) {
			for(ServerClientDisconnectListener l: this.disconnectListeners) {
				l.onDisconnect(this);
			}
			this.disconnectListeners.clear();
		}
		for(ServerFunction func : this.funcMap.values()) {
			func.removeNoopable(this);
		}
		
		this.funcMap.clear();
	}
	
	@Override
	public String getClientId() {
		return this.clientID;
	}
	
	@Override
	public Iterable<ServerFunction> getFunctions() {
		return this.funcMap.values();
	}
	
	@Override
	public int getLocalPort() {
		return this.conn.getLocalPort();
	}
	
	@Override
	public int getPort() {
		return this.conn.getPort();
	}
	
	@Override
	public GearmanPacket getStatus() {
		StringBuilder sb = new StringBuilder();
		sb.append("NA ");
		sb.append(this.conn.getHostAddress());
		sb.append(" ");
		sb.append(this.clientID);
		/*sb.append(" ");
		if(this.startTime != null){
			sb.append(this.startTime.getTime());
		}
		else{
			sb.append("NA");
		}
		sb.append(" ");
		if(this.endTime != null){
			sb.append(this.endTime.getTime());
		}
		else{
			sb.append("NA");
		}*/
		sb.append(" : ");
		
		
		for(ServerFunction f : this.funcMap.values()) {
			sb.append(f.getName().toString(GearmanConstants.UTF_8));
			sb.append(' ');
		}
		
		sb.append('\n');
		
		return GearmanPacket.createTEXT(sb.toString());
	}
	
	@Override
	public GearmanPacket getTimeStatus() {
		StringBuilder sb = new StringBuilder();
		sb.append("NA ");
		sb.append(this.conn.getHostAddress());
		sb.append(" ");
		sb.append(this.clientID);
		sb.append(" ");
		if(this.startTime != null){
			sb.append(this.startTime.getTime());
		}
		else{
			sb.append("NA");
		}
		sb.append(" ");
		if(this.endTime != null){
			sb.append(this.endTime.getTime());
		}
		else{
			sb.append("NA");
		}
		sb.append(" : ");
		
		
		for(ServerFunction f : this.funcMap.values()) {
			sb.append(f.getName().toString(GearmanConstants.UTF_8));
			sb.append(' ');
		}
		
		sb.append('\n');
		
		return GearmanPacket.createTEXT(sb.toString());
	}
	
	@Override
	public void grabJob() {
		//System.out.println("Starting to grab job " + System.currentTimeMillis());
		for(ServerFunction func : this.funcMap.values()) {
			if(func.grabJob(this))
				return;
		}
		this.conn.sendPacket(GearmanPacket.NO_JOB, null  /*TODO*/);
	}
	
	@Override
	public void grabJobUniq() {
		//System.out.println("Starting to grab job " + this.clientID + " " + System.currentTimeMillis());
		for(ServerFunction func : this.funcMap.values()) {
			if(func.grabJob(this)){
				this.startTime = new Date();
				this.endTime = null;
				return;
			}
		}
		this.conn.sendPacket(GearmanPacket.NO_JOB, null  /*TODO*/);
	}

	
	public void jobCOmplete(){
		this.startTime = null;
		this.endTime = new Date();
	}
	@Override
	public boolean isClosed() {
		return this.isClosed;
	}
	
	@Override
	public boolean isForwardsExceptions() {
		return this.isForwardsExceptions;
	}
	
	@Override
	public void noop() {
		synchronized(funcMap) {
			if(!isSleeping) return;
			this.isSleeping=false;
			
			this.conn.sendPacket(GearmanPacket.NOOP, null  /*TODO*/);
		}
	}	
	
	
	@Override
	public boolean removeDisconnectListener(ServerClientDisconnectListener listener) {
		synchronized(this.disconnectListeners) {
			
			return this.disconnectListeners.remove(listener);
		}
	}
	
	@Override
	public void reset() {
	}
	
	@Override
	public void sendExceptionPacket(GearmanPacket packet, GearmanCallbackHandler<GearmanPacket, SendCallbackResult> callback) {
		assert packet.getPacketType().equals(GearmanPacket.Type.WORK_EXCEPTION);
		if(this.isForwardsExceptions)
			this.conn.sendPacket(packet, callback);
	}
	
	@Override
	public void sendPacket(GearmanPacket packet, GearmanCallbackHandler<GearmanPacket, SendCallbackResult> callback) {
		this.conn.sendPacket(packet, callback);
	}
	
	@Override
	public void setClientId(String id) {
		this.clientID = id;
	}
	
	@Override
	public void setForwardsExceptions(boolean value) {
		this.isForwardsExceptions = value;
	}
	
	@Override
	public void sleep() {
		synchronized(funcMap) { this.isSleeping=true; }
		
		
		for(ServerFunction func : this.funcMap.values()) {
			//System.out.println(func.toString());
			if(!func.queueIsEmpty()) {
				this.noop();
				return;
			}
		}
	}
	
	@Override
	protected final void finalize() throws Throwable {
		this.close();
	}
}
