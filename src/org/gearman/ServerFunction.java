package org.gearman;

import org.gearman.ServerJob.JobPriority;

import org.gearman.util.ByteArray;

public interface ServerFunction {

	
	
	public void addNoopable(final ServerClient noopable) ;
	public void removeNoopable(final ServerClient noopable);
	public void setMaxQueue(final int size);
	
	public ByteArray getName() ;
	
	public boolean queueIsEmpty();
	
	public void createJob(final ByteArray uniqueID, final byte[] data, final JobPriority priority, final ServerClient creator, boolean isBackground);
	
	public boolean grabJob(final ServerClient worker);
	public Integer getWorkingCount();
	
	public void removeJobs();
	
	public void wakeUp();
	
}
