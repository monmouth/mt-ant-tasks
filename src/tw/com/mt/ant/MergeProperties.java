/*
 * MergeProperties.java    1.0 2014/3/11
 *
 * Copyright (c) 2014-2030 Monmouth Technologies, Inc.
 * http://www.mt.com.tw
 * 10F-1 No. 306 Chung-Cheng 1st Road, Linya District, 802, Kaoshiung, Taiwan
 * All Rights Reserved.
 *
 * This software is the confidential and proprietary information of Monmouth
 * Technologies, Inc. You shall not disclose such Confidential Information and
 * shall use it only in accordance with the terms of the license agreement you
 * entered into with Monmouth Technologies.
 */
package tw.com.mt.ant;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * Merge multiple property files. Original author is David McTavish,
 * This is our first custom ant task.
 *
 * @version 1.0 2014/3/11
 * @author ken
 * @see http://marc.info/?l=ant-user&m=106442688632164&w=2
 *
 */
public final class MergeProperties extends Task {

    /** determines source .properties file. */
    private File mergeFile;
    /** determines property over-ride file. */
    private File importFile;
    /** determines where final properties are saved. */
    private File destFile;
    /** stores a collection of properties added to merged file. */
    private HashMap<Object, Object> map = new HashMap<Object, Object>();

    /**
     * Configures the source input file to read the source properties.
     *
     * @param file
     *            A File object representing the source .properties file to
     *            read.
     */
    public void setFile(final File file) {
        mergeFile = file;
    }

    /**
     * Configures the destination file to overwrite the properties provided in
     * the source file.
     *
     * @param file
     *            A File object representing the destination file to merge the
     *            combined properties into.
     */
    public void setImportFile(final File file) {
        importFile = file;
    }

    /**
     * Configures the destination file to write the combined properties.
     *
     * @param file
     *            A File object representing the destination file to merge the
     *            combined properties into.
     */
    public void setToFile(final File file) {
        destFile = file;
    }

    /**
     * Method invoked by the ant framework to execute the action associated with
     * this task.
     */
    @Override
    public void execute() {
        // validate provided parameters
        validate();

        // read source .properties
        List<FileContents> newFile = new ArrayList<FileContents>();
        loadFile(mergeFile, newFile);
        loadFile(importFile, newFile);

        // iterate through source, and write to file with updated properties
        writeFile(newFile);
    }

    /**
     * Validate that the task parameters are valid.
     */
    private void validate() {
        map.clear();
        if (!importFile.canRead()) {
            final String message = "Unable to read from " + importFile + ".";
            throw new BuildException(message);
        }
        if (!mergeFile.canRead()) {
            final String message = "Unable to read from " + mergeFile + ".";
            throw new BuildException(message);
        }
        if (!destFile.canWrite()) {
            try {
                destFile.createNewFile();
            } catch (IOException e) {
                throw new BuildException("Unable to write to "
                    + destFile + ".");
            }
        }
    }

    /**
     * Reads the contents of the selected file and returns them in a List that
     * contains String objects that represent each line of the file in the order
     * that they were read.
     *
     * @param file
     *            The file to load the contents into a List.
     * @param fileContents
     *            original list of the contents of the file
     * @return a List of the contents of the file where each line of the file is
     *         stored as an individual String object in the List in the same
     *         physical order it appears in the file.
     */
    private List<FileContents> loadFile(final File file,
            final List<FileContents> fileContents) {
        try {
            InputStream is = new FileInputStream(file);
            BufferedReader in = new BufferedReader(
         		   new InputStreamReader(is, "UTF-8"));
            String curLine;
            String comment = "";
            try {
                while ((curLine = in.readLine()) != null) {
                    curLine = curLine.trim();
                    if (curLine.startsWith("#")) {
                        // comment += curLine + "\r\n";
                        comment += curLine + "\n";
                    } else if (curLine.indexOf("=") > 0
                            || curLine.indexOf(":") > 0) {
                        while (curLine.endsWith("\\")) {
                            // curLine += "\r\n" + in.readLine().trim();
                            curLine += "\n" + in.readLine().trim();
                        }
                        FileContents fc = new FileContents();
                        fc.name = curLine.substring(0,
                                getMappingCharPosition(curLine));
                        fc.value = curLine;
                        fc.comment = comment;
                        comment = "";
                        if (fileContents.contains(fc)) {
                            FileContents existing = getExistingElement(
                                    fileContents, fc.name);
                            if (existing != null) {
                                existing.value = fc.value;
                            } else {
                                fileContents.add(fc);
                            }
                        } else {
                            fileContents.add(fc);
                        }
                    }
                }
            } catch (Exception e) {
                throw new BuildException("Could not read file:" + file, e);
            } finally {
                in.close();
            }
        } catch (IOException ioe) {
            // had an exception trying to open the file
            throw new BuildException("Could not read file:" + file, ioe);
        }
        return fileContents;
    }

    /**
     * Get the mapping character('=' or ':')'s position.
     * @param curLine current line
     * @return an integer of the position of mapping character.
     */
    private int getMappingCharPosition(final String curLine) {
        int position = -1;
        position = curLine.indexOf("=");
        if (position >= 0) {
            return position;
        }
        position = curLine.indexOf(":");
        return position;
    }

    /**
     * Get an existed property by its name.
     * @param list a list of existed properties
     * @param name property name
     * @return a property's value
     */
    private FileContents getExistingElement(final List<FileContents> list,
            final String name) {
        Iterator<FileContents> i = list.iterator();
        while (i.hasNext()) {
            FileContents fc = i.next();
            if (fc.getName().equals(name)) {
                return fc;
            }
        }
        return null;
    }

    /**
     * Writes the merged properties to a single file while preserving any
     * comments.
     *
     * @param source
     *            A list containing the contents of the original source file
     * @param merge
     *            A list containing the contents of the file to merge
     * @param props
     *            A collection of properties with their values merged from both
     *            files
     * @throws BuildException
     *             if the destination file can't be created
     */
    private void writeFile(List fileContents) {
        Iterator iterate = fileContents.iterator();
        try {
            //FileWriter out = new FileWriter(destFile);
			OutputStreamWriter out = new OutputStreamWriter(
					new FileOutputStream(destFile), "UTF-8");
            PrintWriter p = new PrintWriter(out);
            try {
                // write original file with updated values
                while (iterate.hasNext()) {
                    FileContents fc = (FileContents) iterate.next();
                    if (fc.comment != null && !fc.comment.equals("")) {
                        p.println();
                        p.print(fc.comment);
                    }
                    p.println(fc.value);
                }
            } catch (Exception e) {
                throw new BuildException("Could not write file: " + destFile, e);
            } finally {
                out.close();
            }
        } catch (IOException IOe) {
            throw new BuildException("Could not write file: " + destFile, IOe);
        }
    }

    protected class FileContents {
        public String name;
        public String comment;
        public int order;
        public String value;

        public String getName() {
            return name;
        }

        public boolean equals(Object obj) {
            if (obj instanceof FileContents) {
                FileContents fc = (FileContents) obj;
                if (fc.getName().equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static void main(String[] args) throws Exception{
        MergeProperties merger = new MergeProperties();
        merger.setFile(new File("/tmp/tm-message.U8.TXT"));
        merger.setImportFile(new File("/tmp/cec-message.U8.TXT"));
        merger.setToFile(new File("/tmp/merged-message.U8.TXT"));
        merger.execute();
        System.out.println("done!");
    }

}

