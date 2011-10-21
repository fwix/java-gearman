package org.gearman;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.sql.*;
import java.util.Map;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;




public class ThrottleConnector {
	
	public static String YAML_DB = "db";
	public static String YAML_USER = "user";
	public static String YAML_PASSWD = "passwd";
	public static String YAML_HOST = "host";
	public static String ENV_VARIABLE = "FWIX_ENV";
	public static String YAML_ENV_VARIABLE = "YAML_PATH_FILE";
	public static String default_yaml_path = "/home/web/fwix/fwix_config/database/appstatus.yml";
	public static String SQL_QUERY = "SELECT function_name AS function, throttle from gearman_throttle where active = 1 and function_name is not NULL";
	public static String COUNT_QUERY = "SELECT COUNT(*) as count from gearman_server where active = 1";
	
	
	Connection conn = null;

	public ThrottleConnector(){
		
		yamlData dbAttrs = new yamlData();
		
		String driver =   "com.mysql.jdbc.Driver";
		String userName = dbAttrs.userName ;
		String passwd = dbAttrs.passwd;
		String dbName = dbAttrs.database;
		String host =  dbAttrs.host + "/"; 
		String url = "jdbc:mysql://";
		
		try{
			Class.forName(driver).newInstance();
			conn = DriverManager.getConnection(url+host+dbName,userName,passwd);
		
			
		}catch (java.lang.ClassNotFoundException e){
		     System.err.println("Cannot find driver class");
		     //System.exit(1);
		}catch (java.sql.SQLException e){
			System.err.println(e);
		     System.err.println("Cannot get connection");       
		     //System.exit(1);
		}catch(Exception e){
			System.err.println(e);
		}
		
	}
	
	public void setThrottle(){
		
		try{
			Statement stmt=conn.createStatement();
			//TODO: add active = 1
			ResultSet results = stmt.executeQuery(SQL_QUERY);
			while(results.next()){
				String function = results.getString("function");
				int throttle = results.getInt("throttle");
				Main.setThrottle(function, throttle);
				//Main.throttleMap.put(function, throttle);
			}
		}
		catch(java.sql.SQLException e){
		      System.err.println("Cannot create SQL statement");
		}
		
	}
	
	public void getServerCount(){
		int count = 0;
		try{
			Statement stmt=conn.createStatement();
			ResultSet results = stmt.executeQuery(COUNT_QUERY);
			while(results.next()){
				count = results.getInt("count");
				Main.setServerCount(count);
				
			}
		}
		catch(java.sql.SQLException e){
		      System.err.println("Cannot create SQL statement");
		}
		
		
		
	}
	
	
	public void closeConnection(){
		try{
			conn.close();
		}
		catch(java.sql.SQLException e){
		      System.err.println("Cannot close connection");
			
		}
	}
	
	
	public class yamlData{
		String userName = null;
		String passwd = null;
		String database = null;
		String host = null;
		
		public yamlData(){
			
			
			try {
				Map<String, String> env = System.getenv();
				String filePath = env.get(YAML_ENV_VARIABLE);
				if(filePath == null){
					filePath = default_yaml_path;
				}
				YamlReader reader = new YamlReader(new FileReader(filePath));
				Object object = reader.read();
				Map yamlMap = (Map)object;
				
				String currentEnv = env.get(ENV_VARIABLE);
				Object envObject = yamlMap.get(currentEnv.toLowerCase());
				Map map = (Map)envObject;
				this.database = (String)map.get(YAML_DB);
				this.userName = (String)map.get(YAML_USER);
				this.passwd = (String)map.get(YAML_PASSWD);
				this.host = (String)map.get(YAML_HOST);
		
			} catch (FileNotFoundException e) {
				//e.printStackTrace();
				System.err.println("File not found");
			}catch (YamlException e){
				e.printStackTrace();
			}
			catch (Exception e){
				
				System.err.println("Cannot find yaml file");
			}

		}
		
	}

}
