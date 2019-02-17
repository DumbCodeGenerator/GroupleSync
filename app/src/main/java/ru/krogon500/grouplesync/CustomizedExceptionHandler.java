package ru.krogon500.grouplesync;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.DateFormat;
import java.util.Date;

public class CustomizedExceptionHandler implements UncaughtExceptionHandler {

    private final UncaughtExceptionHandler defaultUEH;
    private final String localPath;
    public CustomizedExceptionHandler(String localPath) {
        this.localPath = localPath;
        //Getting the the default exception handler
        //that's executed when uncaught exception terminates a thread
        defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
    }

    public void uncaughtException(Thread t, Throwable e) {

        //Write a printable representation of this Throwable
        //The StringWriter gives the lock used to synchronize access to this writer.
        final Writer stringBuffSync = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringBuffSync);
        e.printStackTrace(printWriter);
        String stacktrace = stringBuffSync.toString();
        printWriter.close();

        if (localPath != null) {
            writeToFile(stacktrace);
        }

        //Used only to prevent from any code getting executed.
        // Not needed in this example
        defaultUEH.uncaughtException(t, e);
    }

    private void writeToFile(String currentStacktrace) {
        try {

            //Gets the Android external storage directory & Create new folder Crash_Reports
            File dir = new File(localPath,
                    "Crash_Reports");
            if (!dir.exists())
                dir.mkdirs();

            DateFormat dateFormat = DateFormat.getDateTimeInstance();
            String filename = dateFormat.format(new Date()) + ".stacktrace";

            // Write the file into the folder
            File reportFile = new File(dir, filename);
            FileWriter fileWriter = new FileWriter(reportFile);
            fileWriter.append(currentStacktrace);
            fileWriter.flush();
            fileWriter.close();
        } catch (Exception e) {
            Log.e("ExceptionHandler", e.getMessage());
        }
    }

}