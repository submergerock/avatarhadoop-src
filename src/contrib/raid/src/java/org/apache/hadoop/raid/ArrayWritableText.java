package org.apache.hadoop.raid;

import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;

public class ArrayWritableText extends ArrayWritable{

	public ArrayWritableText() {
		super(Text.class);
		}
	public ArrayWritableText(Class<? extends Writable> valueClass) {
		super(valueClass);
	}
	public ArrayWritableText(Class<? extends Writable> valueClass, Writable[] values) {
		super(valueClass,values);
	}
  }
