package org.apache.hadoop.hdfs.job;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.BlockingQueue;

import org.apache.hadoop.fs.FSDataInputStream;


public interface WriteObject {
	
	public void 	AddWriteList(BlockingQueue queue);
	public void 	Write(FileOutputStream out);
	public boolean ReadCDRFromHDFS(FSDataInputStream stream);
	public boolean Read( FileInputStream finput);
	public boolean decode(byte[] content) ;
}
