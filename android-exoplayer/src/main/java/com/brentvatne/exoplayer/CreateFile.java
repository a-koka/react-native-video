package com.brentvatne.exoplayer;

import com.google.android.exoplayer2.upstream.cache.*;
import com.google.android.exoplayer2.upstream.*;
import android.util.Log;
import com.google.android.exoplayer2.C;
import java.io.*;
import android.os.AsyncTask;
import java.lang.ref.WeakReference;
import android.os.Environment;
import com.facebook.react.bridge.ReactContext;
import android.net.Uri;
import java.io.StringWriter;
import java.io.PrintWriter;
import android.util.Log;

public class CreateFile extends AsyncTask<CreateFileParams, Void, String> {
    private VideoEventEmitter eventEmitter;

    public CreateFile(VideoEventEmitter eventEmitter) {
        this.eventEmitter = eventEmitter;
    }
    
    @Override
    protected String doInBackground(CreateFileParams... arguments) {
        ReactContext context = arguments[0].context;
        Cache cache = arguments[0].cache;
        String relativePath = arguments[0].relativePath;
        Uri uri = arguments[0].uri;

        HttpDataSource myUpstreamDataSource = new DefaultHttpDataSourceFactory(DataSourceUtil.getUserAgent(context)).createDataSource();
        DataSource cacheReadDataSource = new FileDataSource();
        DataSink cacheWriteDataSink = new CacheDataSink(cache, 100L * 1024L * 1024L, 0, false);
        CacheDataSource dataSource = new CacheDataSource(cache, myUpstreamDataSource, cacheReadDataSource, cacheWriteDataSink, CacheDataSource.FLAG_BLOCK_ON_CACHE, null);

        FileOutputStream outFile = null;
        int bytesRead = 0;
        try{
            File file2 = new File(context.getFilesDir().getAbsolutePath() + relativePath);
            if(!file2.exists()){
                file2.createNewFile();
            }
            outFile = new FileOutputStream(file2);
            DataSpec dataSpec = new DataSpec(uri);
            dataSource.open(dataSpec);
            byte[] data = new byte[1024];
            while(bytesRead != C.RESULT_END_OF_INPUT){
                bytesRead = dataSource.read(data, 0, data.length);
                if(bytesRead != C.RESULT_END_OF_INPUT){
                    outFile.write(data, 0, bytesRead);
                }
            }
        } catch(Exception e){
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return sw.toString(); // stack trace as a string
        } finally{
            try {
                dataSource.close();
                outFile.flush();
                outFile.close();
            } catch(IOException e) {
                // Ignore
            }
        }
        return relativePath;
    }

    @Override
    protected void onPostExecute(String relativePath) {
        eventEmitter.fileSaved(relativePath);
    }
}