package cc;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.nutch.tools.arc.ArcInputFormat;

import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ExtractorBase;
import de.l3s.boilerpipe.extractors.KeepEverythingWithMinKWordsExtractor;

public class ArcReadTest extends Configured implements Tool {

  public static void main(String args[]) throws Exception {
    ToolRunner.run(new ArcReadTest(), args);
  }
    
  public int run(String[] args) throws Exception {
        
    if (args.length!=2) {
      throw new RuntimeException("usage: ArcReadTest <input> <output>");
    }
    
    JobConf conf = new JobConf(getConf(), getClass());
    conf.setJobName("ArcReadTest");
    
    conf.setOutputKeyClass(Text.class);
    conf.setOutputValueClass(Text.class);
    conf.set("mapred.compress.map.output", "true");
    conf.set("mapred.map.output.compression.codec", "com.hadoop.compression.lzo.LzopCodec");    
    
    conf.setInputFormat(ArcInputFormat.class);
    conf.setMapperClass(ArcProcessor.class);    
    
    FileInputFormat.addInputPath(conf, new Path(args[0]));
    FileOutputFormat.setOutputPath(conf, new Path(args[1]));

    JobClient.runJob(conf);

    return 0;
  }

  
  private static class ArcProcessor extends MapReduceBase implements Mapper<Text,BytesWritable,Text,Text> {
    
    enum COLUMNS { URL, IP, DTS, MIME_TYPE, SIZE };    
    private ExtractorBase extractor = new KeepEverythingWithMinKWordsExtractor(5);
    
    public void map(Text k, BytesWritable v, OutputCollector<Text, Text> collector, Reporter reporter) throws IOException {
   
      String headerColumns[] = k.toString().split(" ");      
      if (headerColumns.length != COLUMNS.values().length) {
        System.err.println("dodgy header row? ["+k+"]");
        reporter.getCounter("arc_processor", "dodgy_header").increment(1);
        return;
      }
      
      String mime_type = headerColumns[COLUMNS.MIME_TYPE.ordinal()];
      reporter.getCounter("mime_types", mime_type).increment(1);      
      if (!mime_type.equals("text/html")) {
        return;
      }
            
      String url = headerColumns[COLUMNS.URL.ordinal()];
      String dts = headerColumns[COLUMNS.DTS.ordinal()];
    
      String httpResponse = new String(v.getBytes(), 0, v.getLength(), "UTF8");
      String visibleText = extractVisibleText(httpResponse);    
      
      collector.collect(new Text(url+" "+dts), new Text(visibleText));
      
    }
   
    public String extractVisibleText(String httpResponse) {
      int htmlStartIdx = httpResponse.indexOf("\r\n\r\n"); // ie end of header      
      String html = httpResponse.substring(htmlStartIdx);    
      try {
        String textOfPage = extractor.getText(html);
        return textOfPage.replaceAll("\\s+"," ");             
      } catch (BoilerpipeProcessingException e) {
        e.printStackTrace();
        return "";
      }
    }      
    
  }
  
}
