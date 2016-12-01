package org.bgi.flexlab.gaea.data.structure.vcf.index;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.bgi.flexlab.gaea.util.HdfsFileManager;

import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.index.tabix.TabixIndexCreator;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.AsciiLineReaderIterator;
import htsjdk.tribble.util.LittleEndianInputStream;
import htsjdk.tribble.util.LittleEndianOutputStream;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;


public class IndexCreator {
			
	private String file;
	
	private FileSystem fs;
	
	private Configuration configuration;
						
	public IndexCreator(String file) {
		configuration = new Configuration();
		this.file = file;
		fs = HdfsFileManager.getFileSystem(new Path(this.file), configuration);
	}
	
	public Index finalizeIndex() throws IOException {
		Path idxfile = format(file);
		Index index = null;
		FSDataInputStream is = null;
		try {
			if(fs.exists(idxfile)) {
				is = fs.open(idxfile);
				index = new TabixIndex(new LittleEndianInputStream(is));
			} else {
				is = fs.open(new Path(file));
				index = createIndex(is);
				LittleEndianOutputStream os = new LittleEndianOutputStream(HdfsFileManager.getOutputStream(idxfile, configuration));
				index.write(os);
				os.close();
			}
		} finally {
			fs.close();
		} 
		
		return index; 
	}
	
	
	private static Index createIndex(InputStream is) throws IOException {
		TabixIndexCreator ctor = new TabixIndexCreator(TabixFormat.VCF);
		VCFCodec codec = new VCFCodec();
		VariantContext lastContext = null;
        VariantContext currentContext;
        final Map<String, VariantContext> visitedChromos = new HashMap<String, VariantContext>();
        AsciiLineReader lineReader = new AsciiLineReader(is);
        AsciiLineReaderIterator iterator = new AsciiLineReaderIterator(lineReader);
        codec.readActualHeader(iterator);
        while (iterator.hasNext()) {
            final long position = iterator.getPosition();
            currentContext = codec.decode(iterator.next());
            checkSorted(lastContext, currentContext);
            //should only visit chromosomes once
            final String curChr = currentContext.getChr();
            final String lastChr = lastContext != null ? lastContext.getChr() : null;
            if(!curChr.equals(lastChr)){
                if(visitedChromos.containsKey(curChr)){
                	throw new RuntimeException("Input file must have contiguous chromosomes.");
                }else{
                    visitedChromos.put(curChr, currentContext);
                }
            }

            ctor.addFeature(currentContext, position);

            lastContext = currentContext;
        }

        iterator.close();
        
        return ctor.finalizeIndex(iterator.getPosition());
	}
	
	private static void checkSorted(final VariantContext lastContext, final VariantContext currentContext){
	        // if the last currentFeature is after the current currentFeature, exception out
        if (lastContext != null && currentContext.getStart() < lastContext.getStart() && lastContext.getChr().equals(currentContext.getChr()))
            throw new RuntimeException("Input file is not sorted by start position.");
	}
	
	private Path format(String inputFile) {
		return new Path(inputFile + ".gaeaidx");
	}
}
