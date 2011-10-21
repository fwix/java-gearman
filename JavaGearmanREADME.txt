Java Gearman Server --  by FWIX


This server is a derived java gearman from the excellent work by Isaiah V.
The code can be found at http://code.google.com/p/java-gearman-service/ (we used version 0.4)
For in depth description about Java-gearman-services read ReadMe.txt.
This document is to give you a better understanding of the additional features added by FWIX

Setup:
We have a gearman setup where any worker can accept any job irrespective of functionality.
The gearman server is in Java and the client/worker side are implemented in Python.

Client and worker side:
We have a class called datum which is used to create jobs to be submitted to the server.
The datum class has the following class variables:
self.import : the import path to the function
self.func : the actual function name
self.args : args used in the execution of the function
self.kwargs: kwargs used in the execution of the function

To submit a job we create a datum object with the necessary values and then we call
client.submit_job with the datum as the data.

When a worker receives a job, it calls datum.run() which then runs the function using all the information.

def run(self):
  module = importlib.import_module(self.import)
  fun = eval('module.' + self.func)
  result = func(*args, **kwargs)


NOTE: you will need the python gearman libraries to work with these addition features is located at:
git@github.com:fwix/python-gearman.git

Addition Features:
1. Ability to Round robin
2. Add throttle to jobs
3. Kill jobs in queue
4. Stats about server
5. More insight into worker status

Along with the Java gearman server, we have the modified python libraries
which go hand in hand to make full use of the new features.

NOTE: the server is totally backward compatible and old client and worker
libraries will not fail with this server.



1. Ability to Round robin:
If you have a worker register to Java gearman with the name "gm_task" the jobs will be in round
robin order. 

Example:
On server side you have 3 different tasks with names task_1, task_2 and task_3.
If you have a worker register with the name "gm_task", the worker will get task_1 first, then task_2
then task_3, then task_1 and so on.

NOTE: please do not submit any tasks with the name gm_task. gm_task is the magic word for round robin
on this server.


2. Add throttle to job
You can add throttle to a certain task thus limiting the number of workers that can run that particular 
task at any point in time.

Using our custom python libraries you can insert throttle to a given task by using the admin client function
put_throttle(task_name, throttle)

Example:
>import gearman
>admin = gearman.GearmanAdminClient(host_list=['localhost'])
>admin.put_throttle("my_task", 2)

To maintain throttles through gearman server restarts we have a table named "gearman_throttle".

mysql> desc gearman_throttle;
+-----------------------+---------------------+------+-----+---------+----------------+
| Field                 | Type                | Null | Key | Default | Extra          |
+-----------------------+---------------------+------+-----+---------+----------------+
| id                    | int(11)             | NO   | PRI | NULL    | auto_increment |
| modified_at           | datetime            | YES  |     | NULL    |                |
| created_at            | datetime            | YES  |     | NULL    |                |
| throttle              | int(11)             | NO   |     | NULL    |                |
| active                | tinyint(3) unsigned | YES  |     | NULL    |                |
| function_name         | varchar(100)        | YES  |     | NULL    |                |
+-----------------------+---------------------+------+-----+---------+----------------+

Every time we insert a throttle, we also put an entry into this table. This helps us keep track of
all the throttles we have inserted on the gearman server.

Everytime the server starts up, it also queries this table to get all the throttles and maintains it
throughout its lifetime. Thus throttles in this table are maintained between restarts.

This table is not required and the server will continue to work properly even without this table.

If you do want the ability to maintain throttles and decide to have this table you will also need
to have the yaml file which looks like:

development: &dev
     db: {database}
     user: {user}
     passwd: {password}
     host: localhost
   
production: &prod
    db: {prod database}
    user: {prod user}
    passwd: {prod passwd}
    host: {prod host}
    

You also need to have an environment variable called FWIX_ENV set(either development or production 
for our example above).

The gearman server uses the FWIX_ENV to get the information to connect to the mysql database.

Below is the query the server runs to get the throttle:
"SELECT function_name AS function, throttle from gearman_throttle where active = 1 and function_name is not NULL"

We have have added the ability to maintain throttle across multiple servers.
If you have more than 1 server running, the throttle will be divided by the total number of servers,
and that will be the throttle per server.

To have this ability you need a table called "gearman_server" with the same setup as above
mysql> desc gearman_server;
+-----------+--------------+------+-----+---------+----------------+
| Field     | Type         | Null | Key | Default | Extra          |
+-----------+--------------+------+-----+---------+----------------+
| id        | bigint(20)   | NO   | PRI | NULL    | auto_increment |
| host_name | varchar(100) | NO   |     | NULL    |                |
| port      | int(11)      | NO   |     | NULL    |                |
| active    | tinyint(4)   | NO   |     | 1       |                |
+-----------+--------------+------+-----+---------+----------------+


If you have inserted a throttle and want to remove the throttle, the below function will remove the throttle
put_throttle(task_name, "NOTHROTTLE")


Example:
>>>import gearman
>>>admin = gearman.GearmanAdminClient(host_list=['localhost'])
>>>admin.put_throttle("my_task", "NOTHROTTLE")


NOTE: both these tables are not required and things will work normally without them.


3. Kill jobs in queue
You can also kill all the tasks in a queue, using the below function.
put_throttle(task_name, "KILL")

this will kill all the jobs currently queued with that task name.

Example:
>>>import gearman
>>>admin = gearman.GearmanAdminClient(host_list=['localhost'])
>>>admin.put_throttle("my_task", "KILL")


4. Stats about server
You can get the below stats about the server using
admin.get_memstats()


ActiveThreads: number of active threads
TotalThread: total number of threads
Uptime: total time (in seconds) the server has been up
Memused: Total memory used (in bytes)

Example:
>>>import gearman
>>>admin = gearman.GearmanAdminClient(host_list=['localhost'])
>>> admin.get_memstats()
{'ActiveThreads': 237, 'TotalThreads': 240, 'UpTime': 81564, 'MemUsed': 1066655672}


5. More insight into worker status
Just like get_workers() you can use get_worker_time() to get a few more extra stats:

start_time : last time the worker accepted a job
end_time: last time the worker completed a jab

If start_time is populated end time will be NA, telling us that the worker is running a job
If end_time is populated the start time will be NA, telling us the last time the worker completed a job and hasn't started working on another job yet


Example:
>>>import gearman
>>>admin = gearman.GearmanAdminClient(host_list=['localhost'])
>>> admin.get_worker_time()
({'file_descriptor': 'NA', 'tasks': ('gm_task', ''), 'ip': '127.0.0.1', 'start_time': 'NA', 'end_time': 'NA', 'client_id': '757382575'}, {'file_descriptor': 'NA', 'tasks': ('',), 'ip': '192.168.1.172', 'start_time': 'NA', 'end_time': 'NA', 'client_id': '575441368'})



Execution
There is a jar file included Java-gearman.jar which can be used to launch the server

java -Xmx1G -jar Java-gearman.jar -p4730

Xmx - flag is to show the max memory used by this process


Please feel free to send feedback, bug fixes or questions to
swapan@fwix.com