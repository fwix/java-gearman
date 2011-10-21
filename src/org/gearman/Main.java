package org.gearman;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.gearman.util.ArgumentParser;
import org.gearman.util.ArgumentParser.Option;


/**
 * The class that starts the standalone server
 * @author isaiah.v
 */
class Main {
	
	private static String VERSION = "1317167229";
	
	private static ConcurrentHashMap<String, Integer> throttleMap = new ConcurrentHashMap<String, Integer>();
	private static int serverCount = 0;
	static final Runtime runtime = Runtime.getRuntime ();
	
	private static long startTime = System.currentTimeMillis();
	
	public static void setThrottle(String name, Integer throttle){
		name = name.toLowerCase();
		throttleMap.put(name, throttle);
		
	}
	
	public static void removeThrottle(String name){
		name = name.toLowerCase();
		throttleMap.remove(name);
	}
	
	public static Integer getThrottle(String name){
		name = name.toLowerCase();
		 return throttleMap.get(name);
		
	}
	
	public static int getServerCount(){
		return serverCount;
	}
	
	public  static void setServerCount(int count){
		serverCount = count;
	}
	private static String HELP = 
		VERSION + "\n" +
		"\n" +
		"usage:\n" +
		"java [jvm options] -jar "+VERSION+".jar [server options]\n" +
		"\n" +
		"Options:\n" +
		"   -p PORT   --port=PORT     Defines what port number the server will listen on (Default: 4730)\n" +
		"   -v        --version       Display the version of java gearman service and exit\n" +
		"   -?        --help          Print this help menu and exit";
	
	/**
	 * Prints the current version and 
	 * @param out
	 */
	private static final void printHelp(PrintStream out) {
		out.println(HELP);
		System.exit(0);
	}
	
	private static final void printVersion() {
		System.out.println(VERSION);
		System.exit(0);
	}
	
	/**
	 * Starts the standalone gearman job server.
	 * @throws IOException 
	 */
	public static void main(final String[] args) {
		try {
			Gearman gearman = new Gearman();
			GearmanServer server = gearman.createGearmanServer();
			((ServerImpl)server).closeGearmanOnShutdown(true);
			
			server.openPort(new Main(args).getPort());
		} catch (Throwable th) {
			System.err.println(th.getMessage());
			System.err.println();
			printHelp(System.err);
		}
	}
	
	private int port = 4730;
	
	private Main(final String[] args) {
		final ArgumentParser ap = new ArgumentParser();
		
		boolean t1, t2, t3;
		t1 = ap.addOption('p', "port", true);
		t2 = ap.addOption('v', "version", false);
		t3 = ap.addOption('?', "help", false);
		
		assert t1&&t2&&t3;
		

		try{
			ThrottleConnector throttle = new ThrottleConnector();
			throttle.getServerCount();
			throttle.setThrottle();
			throttle.closeConnection();
		}
		catch(Exception e){
			System.err.println("No throttle inserted, continuing with server start");
		}
		
		ArrayList<String> arguments = ap.parse(args);
		if(arguments==null) {
			System.err.println("argument parsing failed");
			System.err.println();
			printHelp(System.err);
		} else if(!arguments.isEmpty()) {
			System.err.print("received unexpected arguments:");
			for(String arg : arguments) {
				System.err.print(" "+arg);
			}
			System.err.println('\n');
			printHelp(System.err);
		}
		
		for(Option op : ap) {
			switch(op.getShortName()) {
			case 'p':
				try {
					this.port = Integer.parseInt(op.getValue());
				} catch(NumberFormatException nfe) {
					System.err.println("failed to parse port to integer: "+op.getValue());
					System.err.println();
					printHelp(System.err);
				}
				break;
			case 'v':
				printVersion();
			case 'h':
				printHelp(System.out);
			}
		}
	}
	
	private int getPort() {
		return this.port;
	}
	
	
	public static long memUsed(){
		return runtime.totalMemory() - runtime.freeMemory();
			
	}
	
	public static int getThreadCount(){
		return Thread.getAllStackTraces().size();
		
	}
	
	public static int getActiveThreadCount(){
		return Thread.activeCount();
		
	}
	
	public static long getUpTime(){
		
		long totalTime = System.currentTimeMillis() - startTime;
		
		return (totalTime/1000);
		
		
	}
}
