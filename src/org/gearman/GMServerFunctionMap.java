package org.gearman;



import org.gearman.util.ByteArray;
import org.gearman.ServerJob.JobPriority;



public class GMServerFunctionMap {
	

	public static final String GM_TASK = "gm_task";
	public static final ByteArray GM_TASK_BA = new ByteArray(GM_TASK.getBytes());
	
	
	public final ServerFunction getFunction(ServerFunctionMap map, ByteArray name){
		
		
		if (name.toString().equals(GM_TASK)){
			return new GMServerFunction();
			
		}
		
		return map.getFunction(name);
	}
	
	class GMServerFunction implements ServerFunction{
		
		public void removeJobs(){
			
		}
		
		public void wakeUp(){
			
			
		}
		
		public Integer getWorkingCount(){
			
			return null;
		}
		
		public void addNoopable(final ServerClient noopable) {
			// DO NOTHING
		}
		public void removeNoopable(final ServerClient noopable){
			// DO NOTHING
		}
		public void setMaxQueue(final int size){
			//DO NOTHING
			
		}
		
		public ByteArray getName(){
			
			return GM_TASK_BA;
			
			
		}
		
		public boolean queueIsEmpty(){
			
			return GMServerFunctionMap.this.isQueueEmpty();
		}
		
		public void createJob(final ByteArray uniqueID, final byte[] data, final JobPriority priority, final ServerClient creator, boolean isBackground){
			
			throw new RuntimeException("NEVER PUT ANYTHING IN GM_TASK");
			
			
		}


		
		public boolean grabJob(final ServerClient worker){
			
			
			
			return GMServerFunctionMap.this.grabJob(worker);
			
		}

	}
	
	final ServerFunctionMap funcSetHigh = new ServerFunctionMap();
	final ServerFunctionMap funcSetMedium = new ServerFunctionMap();
	final ServerFunctionMap funcSetLow = new ServerFunctionMap();
	
	
	public boolean grabJob(final ServerClient worker){
		
		if (funcSetHigh.grabJob(worker)){
			return true;
		}
		
		if (funcSetMedium.grabJob(worker)){
			return true;
		}
		
		if (funcSetLow.grabJob(worker)){
			return true;
		}
		
		return false;
		
		
	}
	
	
	
	public final void sendStatus(ServerClient client) {
		
		funcSetHigh.sendStatusNoDone(client);
		funcSetMedium.sendStatusNoDone(client);
		funcSetLow.sendStatusNoDone(client);
		
		client.sendPacket(ServerStaticPackets.TEXT_DONE, null /*TODO*/);
		
	}
	
	public void removeJobs(String function){
		
		ByteArray funcNameBA = new ByteArray(function.getBytes());
		
		funcSetHigh.removeJobs(funcNameBA);
		funcSetMedium.removeJobs(funcNameBA);
		funcSetLow.removeJobs(funcNameBA);
		
	}
	
	
	public final void setMaxQueueByName(ByteArray name, int size){
		
		ServerFunction funcHigh = funcSetHigh.getFunctionIfDefined(name);
		if (funcHigh != null){
			funcHigh.setMaxQueue(size);
		}
		
		ServerFunction funcMedium = funcSetMedium.getFunctionIfDefined(name);
		if (funcMedium != null){
			funcMedium.setMaxQueue(size);
		}
		
		ServerFunction funcLow = funcSetLow.getFunctionIfDefined(name);
		if (funcLow != null){
			funcLow.setMaxQueue(size);
		}
		
	}
	
	
	public  void registerClient(ByteArray funcNameBA, final ServerClient client){
		
		

		final ServerFunction funcHigh = this.getFunction(funcSetHigh, funcNameBA);
		final ServerFunction funcMedium = this.getFunction(funcSetMedium, funcNameBA);
		final ServerFunction funcLow = this.getFunction(funcSetLow, funcNameBA);
		
		funcHigh.addNoopable(client);
		funcMedium.addNoopable(client);
		funcLow.addNoopable(client);
		
		MultiServerFunction serverFunc = new MultiServerFunction(funcHigh, funcMedium, funcLow);
	
		client.can_do(serverFunc);
		
		
		
	}
	
	private ServerFunctionMap getFunctionMapPriority(ServerJob.JobPriority priority){
		
		ServerFunctionMap func = null;
		
		switch(priority){
		case HIGH:
			func = funcSetHigh;
			break;
		case MID:
			func = funcSetMedium;
			break;
		case LOW:
			func = funcSetLow;
			break;
			
		}
		
		return func;
		
	}
	
	
	public void createJob(ByteArray funcNameBA, ByteArray uniqueIDBA,byte[] data,ServerJob.JobPriority priority,ServerClient client,boolean isBackground){
		
		
		ServerFunctionMap func = getFunctionMapPriority( priority);
		func.createJob(funcNameBA, uniqueIDBA, data, priority, client, isBackground);
		
		
	}
	
	
	
	
	public int getQueueCount(){
		
		return funcSetHigh.getQueueCount() + funcSetMedium.getQueueCount() + funcSetLow.getQueueCount();
	}
	
	
	public boolean isQueueEmpty(){
		
		return getQueueCount() ==0 ;
		
	}
	
	

}
