package org.gearman;

import org.gearman.ServerJob.JobPriority;
import org.gearman.util.ByteArray;

public class MultiServerFunction implements ServerFunction{
	
	
	ServerFunction high = null;
	ServerFunction medium = null;
	ServerFunction low = null;
	
	public MultiServerFunction (ServerFunction high, ServerFunction medium, ServerFunction low){
		
		assert high!=null;
		assert medium!=null;
		assert low!=null;
		
		
		this.high = high;
		this.medium = medium;
		this.low = low;
		
	}
	
	public void wakeUp(){
		
		
	}
	
	public void removeJobs(){
		
		high.removeJobs();
		medium.removeJobs();
		low.removeJobs();
		
	}
	
	public void addNoopable(final ServerClient noopable) {
		
		high.addNoopable(noopable);
		medium.addNoopable(noopable);
		low.addNoopable(noopable);
		
	}
	public void removeNoopable(final ServerClient noopable){
		high.removeNoopable(noopable);
		medium.removeNoopable(noopable);
		low.removeNoopable(noopable);
		
	}
	public void setMaxQueue(final int size){
		high.setMaxQueue(size);
		medium.setMaxQueue(size);
		low.setMaxQueue(size);
		
	}
	
	public ByteArray getName(){
		
		return high.getName();
		
		
	}
	
	public boolean queueIsEmpty(){
		
		return high.queueIsEmpty() && medium.queueIsEmpty() && low.queueIsEmpty();
		
	}
	
	public void createJob(final ByteArray uniqueID, final byte[] data, final JobPriority priority, final ServerClient creator, boolean isBackground){
		
		ServerFunction serverFunc = null;
		
		switch(priority){
			case HIGH:
				serverFunc = high;
				break;
			case MID:
				serverFunc = medium;
				break;
			case LOW:
				serverFunc = low;
				break;
		}
				
		assert serverFunc != null;
		serverFunc.createJob(uniqueID, data, priority, creator, isBackground);
		
	}


	
	public boolean grabJob(final ServerClient worker){
		
		
		
		if(high.grabJob(worker)){
			return true;
		}
		
		if(medium.grabJob(worker)){
			return true;
		}
		
		if(low.grabJob(worker)){
			return true;
		}
		
		return false;
		
	}

	
	public Integer getWorkingCount(){
		return null;
	}
}
