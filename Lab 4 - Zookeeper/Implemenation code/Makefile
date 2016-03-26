build:
	javac -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar ZkConnector.java Worker.java Client.java JobTracker.java ClientPacket.java JobTrackerThread.java Fileserver.java FileserverHandlerThread.java
runClient:
	java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. Client ug205.eecg.utoronto.ca:8099
runWorker:
	java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. Worker ug205.eecg.utoronto.ca:8099	
runJob:
	java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. JobTracker ug205.eecg.utoronto.ca:8099
runFile:
	java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. Fileserver ug205.eecg.utoronto.ca:8099

clean:
	rm -f *.class
