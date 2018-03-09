package org.bgi.flexlab.gaea.tools.mapreduce.haplotypecaller;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.bgi.flexlab.gaea.data.mapreduce.output.vcf.GaeaVCFOutputFormat;
import org.bgi.flexlab.gaea.data.mapreduce.writable.SamRecordWritable;
import org.bgi.flexlab.gaea.data.mapreduce.writable.WindowsBasedWritable;
import org.bgi.flexlab.gaea.framework.tools.mapreduce.BioJob;
import org.bgi.flexlab.gaea.framework.tools.mapreduce.ToolsRunner;
import org.bgi.flexlab.gaea.framework.tools.mapreduce.WindowsBasedSamRecordMapper;
import org.seqdoop.hadoop_bam.VCFOutputFormat;
import org.seqdoop.hadoop_bam.VariantContextWritable;

public class HaplotypeCaller extends ToolsRunner {

	@Override
	public int run(String[] args) throws Exception {
		BioJob job = BioJob.getInstance();
        Configuration conf = job.getConfiguration();
        
        String[] remainArgs = remainArgs(args, conf);
        HaplotypeCallerOptions options = new HaplotypeCallerOptions();
        options.parse(remainArgs);
        options.setHadoopConf(remainArgs, conf);
        conf.set(VCFOutputFormat.OUTPUT_VCF_FORMAT_PROPERTY, "VCF");
        conf.setBoolean(GaeaVCFOutputFormat.HEADER_MODIFY, true);
        
        job.setHeader(options.getInput(), new Path(options.getHeaderOutput()));
        job.setJobName("Gaea haplotype caller");
        
        job.setJarByClass(HaplotypeCaller.class);
        job.setWindowsBasicMapperClass(WindowsBasedSamRecordMapper.class, options.getWindowSize(),0);
        job.setReducerClass(HaplotypeCallerReducer.class);
        
        job.setNumReduceTasks(options.getReducerNumber());
        job.setOutputKeyValue(WindowsBasedWritable.class,SamRecordWritable.class, NullWritable.class, VariantContextWritable.class);
        
        job.setAnySamInputFormat(options.getInputFormat());
		job.setOutputFormatClass(GaeaVCFOutputFormat.class);
        
        FileInputFormat.setInputPaths(job, options.getInput().toArray(new Path[options.getInput().size()]));
		FileOutputFormat.setOutputPath(job, new Path(options.getVCFOutput()));
		
		return job.waitForCompletion(true) ? 0 : 1;
	}

}
