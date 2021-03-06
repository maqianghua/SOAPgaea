package org.bgi.flexlab.gaea.tools.vcfqualitycontrol2.util;

import com.google.common.io.ByteStreams;

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.util.TabixUtils;
import java.nio.file.Files;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.bgi.flexlab.gaea.data.exception.UserException;
import org.bgi.flexlab.gaea.util.Utils;

import java.io.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

public final class BucketUtils {
    public static final String GCS_PREFIX = "gs://";
    public static final String HDFS_PREFIX = "hdfs://";

    // slashes omitted since hdfs paths seem to only have 1 slash which would be weirder to include than no slashes
    public static final String FILE_PREFIX = "file:";

    // if the channel errors out, re-open up to this many times
    public static final int DEFAULT_GCS_MAX_REOPENS = 20;

    private BucketUtils(){} //private so that no one will instantiate this class

    public static boolean isCloudStorageUrl(final String path) {
        Utils.nonNull(path);
        return path.startsWith(GCS_PREFIX);
    }

    public static boolean isCloudStorageUrl(final java.nio.file.Path path) {
        // the initial "" protects us against a null scheme
        return ("" + path.toUri().getScheme() + "://").equals(GCS_PREFIX);
    }

    /**
     * Returns true if the given path is a HDFS (Hadoop filesystem) URL.
     */
    public static boolean isHadoopUrl(String path) {
        return path.startsWith(HDFS_PREFIX);
    }

    /**
     * Returns true if the given path is a GCS or HDFS (Hadoop filesystem) URL.
     */
    public static boolean isRemoteStorageUrl(String path) {
        return isCloudStorageUrl(path) || isHadoopUrl(path);
    }

    /**
     * Changes relative local file paths to be absolute file paths. Paths with a scheme are left unchanged.
     * @param path the path
     * @return an absolute file path if the original path was a relative file path, otherwise the original path
     */
    public static String makeFilePathAbsolute(String path){
        if (isCloudStorageUrl(path) || isHadoopUrl(path) || isFileUrl(path)){
            return path;
        } else {
            return new File(path).getAbsolutePath();
        }
    }

    /**
     * Open a file for reading regardless of whether it's on GCS, HDFS or local disk.
     *
     * If the file ends with .gz will attempt to wrap it in an appropriate unzipping stream
     *
     * @param path the GCS, HDFS or local path to read from. If GCS, it must start with "gs://", or "hdfs://" for HDFS.
     * @return an InputStream that reads from the specified file.
     */
    public static InputStream openFile(String path) {
        try {
            Utils.nonNull(path);
            InputStream inputStream;
            if (isHadoopUrl(path)) {
                Path file = new org.apache.hadoop.fs.Path(path);
                FileSystem fs = file.getFileSystem(new Configuration());
                inputStream = fs.open(file);
            } else {
                inputStream = new FileInputStream(path);
            }

            if(AbstractFeatureReader.hasBlockCompressedExtension(path)){
            	InputStream in = new BufferedInputStream(inputStream);
            	Utils.nonNull(in);
                if (BlockCompressedInputStream.isValidFile(in)) {
                        return new BlockCompressedInputStream(in);
                } else {
                    return new GZIPInputStream(in);
                }
            } else {
                return inputStream;
            }
        } catch (IOException x) {
            throw new UserException.CouldNotReadInputFile(path, x);
        }
    }

    /**
     * Open a binary file for writing regardless of whether it's on GCS, HDFS or local disk.
     * For writing to GCS it'll use the application/octet-stream MIME type.
     *
     * @param path the GCS or local path to write to. If GCS, it must start with "gs://", or "hdfs://" for HDFS.
     * @return an OutputStream that writes to the specified file.
     */
    public static OutputStream createFile(String path) {
        Utils.nonNull(path);
        try {
            if (isHadoopUrl(path)) {
                Path file = new Path(path);
                FileSystem fs = file.getFileSystem(new Configuration());
                return fs.create(file);
            } else {
                return new FileOutputStream(path);
            }
        } catch (IOException x) {
            throw new UserException.CouldNotCreateOutputFile("Could not create file at path:" + path + " due to " + x.getMessage(), x);
        }
    }

    /**
     * Copies a file. Can be used to copy e.g. from GCS to local.
     *
     * @param sourcePath the path to read from. If GCS, it must start with "gs://", or "hdfs://" for HDFS.
     * @param destPath the path to copy to. If GCS, it must start with "gs://", or "hdfs://" for HDFS.
     * @throws IOException
     */
    public static void copyFile(String sourcePath, String destPath) throws IOException {
        try (
            InputStream in = openFile(sourcePath);
            OutputStream fout = createFile(destPath)) {
            ByteStreams.copy(in, fout);
        }
    }

    /**
     * Deletes a file: local, GCS or HDFS.
     *  @param pathToDelete the path to delete. If GCS, it must start with "gs://", or "hdfs://" for HDFS.
     *
     */
    public static void deleteFile(String pathToDelete) throws IOException {
        if (isHadoopUrl(pathToDelete)) {
            Path file = new Path(pathToDelete);
            FileSystem fs = file.getFileSystem(new Configuration());
            fs.delete(file, false);
        } else {
            boolean ok = new File(pathToDelete).delete();
            if (!ok) throw new IOException("Unable to delete '"+pathToDelete+"'");
        }
    }

    /**
     * Get a temporary file path based on the prefix and extension provided.
     * This file (and possible indexes associated with it) will be scheduled for deletion on shutdown
     *
     * @param prefix a prefix for the file name
     *               for remote paths this should be a valid URI to root the temporary file in (ie. gcs://hellbender/staging/)
     *               there is no guarantee that this will be used as the root of the tmp file name, a local prefix may be placed in the tmp folder for example
     * @param extension and extension for the temporary file path, the resulting path will end in this
     * @return a path to use as a temporary file, on remote file systems which don't support an atomic tmp file reservation a path is chosen with a long randomized name
     *
     */
    public static String getTempFilePath(String prefix, String extension){
        if (isCloudStorageUrl(prefix) || (isHadoopUrl(prefix))){
            final String path = randomRemotePath(prefix, "", extension);
            deleteOnExit(path);
            deleteOnExit(path + Tribble.STANDARD_INDEX_EXTENSION);
            deleteOnExit(path + TabixUtils.STANDARD_INDEX_EXTENSION);
            deleteOnExit(path + ".bai");
            deleteOnExit(path + ".md5");
            deleteOnExit(path.replaceAll(extension + "$", ".bai")); //if path ends with extension, replace it with .bai
            return path;
        } else {
        	try {
                final File file = File.createTempFile(prefix, extension);
                file.deleteOnExit();

                // Mark corresponding indices for deletion on exit as well just in case an index is created for the temp file:
                new File(file.getAbsolutePath() + Tribble.STANDARD_INDEX_EXTENSION).deleteOnExit();
                new File(file.getAbsolutePath() + TabixUtils.STANDARD_INDEX_EXTENSION).deleteOnExit();
                new File(file.getAbsolutePath() + ".bai").deleteOnExit();
                new File(file.getAbsolutePath() + ".md5").deleteOnExit();
                new File(file.getAbsolutePath().replaceAll(extension + "$", ".bai")).deleteOnExit();

                return file.getAbsolutePath();
            } catch (IOException ex) {
                throw new UserException("Cannot create temp file: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Schedule a file to be deleted on JVM shutdown.
     * @param fileToDelete the path to the file to be deleted
     *
     */
    public static void deleteOnExit(String fileToDelete){
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    deleteFile(fileToDelete);
                } catch (IOException e) {
                    
                }
            }
        });
    }

    /**
     * Picks a random name, by putting some random letters between "prefix" and "suffix".
     *
     * @param stagingLocation The folder where you want the file to be. Must start with "gs://" or "hdfs://"
     * @param prefix The beginning of the file name
     * @param suffix The end of the file name, e.g. ".tmp"
     */
    public static String randomRemotePath(String stagingLocation, String prefix, String suffix) {
        if (isHadoopUrl(stagingLocation)) {
            return new Path(stagingLocation, prefix + UUID.randomUUID().toString() + suffix).toString();
        } else {
            throw new IllegalArgumentException("Staging location is not remote: " + stagingLocation);
        }
    }

    /**
     * Returns true if we can read the first byte of the file.
     *  @param path The folder where you want the file to be (local, GCS or HDFS).
     *
     */
    public static boolean fileExists(String path) {
        final boolean MAYBE = false;
        try {
            InputStream inputStream = openFile(path);
            int ignored = inputStream.read();
        } catch (UserException.CouldNotReadInputFile notthere) {
            // file isn't there
            return false;
        } catch (FileNotFoundException x) {
            // file isn't there
            return false;
        } catch (IOException x) {
            // unexpected problem while reading the file. The file may exist, but it's not accessible.
            return MAYBE;
        }
        return true;
    }

    /**
     * Returns the file size of a file pointed to by a GCS/HDFS/local path
     *
     * @param path The URL to the file whose size to return
     * @return the file size in bytes
     * @throws IOException
     */
    public static long fileSize(String path) throws IOException {
        if (isHadoopUrl(path)) {
            Path hadoopPath = new Path(path);
            FileSystem fs = hadoopPath.getFileSystem(new Configuration());
            return fs.getFileStatus(hadoopPath).getLen();
        } else {
            return new File(path).length();
        }
    }

    /**
     * Returns the total file size of all files in a directory, or the file size if the path specifies a file.
     * Note that sub-directories are ignored - they are not recursed into.
     * Only supports HDFS and local paths.
     *
     * @param path The URL to the file or directory whose size to return
     * @return the total size of all files in bytes
     */
    public static long dirSize(String path) {
        try {
            // local file or HDFS case
            Path hadoopPath = new Path(path);
            FileSystem fs = new Path(path).getFileSystem(new Configuration());
            FileStatus status = fs.getFileStatus(hadoopPath);
            if (status == null) {
                throw new UserException.CouldNotReadInputFile(path, "File not found.");
            }
            long size = 0;
            if (status.isDirectory()) {
                for (FileStatus st : fs.listStatus(status.getPath())) {
                    if (st.isFile()) {
                        size += st.getLen();
                    }
                }
            } else {
                size += status.getLen();
            }
            return size;
        } catch (RuntimeIOException | IOException e) {
            throw new UserException("Failed to determine total input size of " + path + "\n Caused by:" + e.getMessage(), e);
        }
    }

    public static boolean isFileUrl(String path) {
        return path.startsWith(FILE_PREFIX);
    }

    /**
     * Given a path of the form "gs://bucket/folder/folder/file", returns "bucket".
     */
    public static String getBucket(String path) {
        return path.split("/")[2];
    }

    /**
     * Given a path of the form "gs://bucket/folder/folder/file", returns "folder/folder/file".
     */
    public static String getPathWithoutBucket(String path) {
        final String[] split = path.split("/");
        final String BUCKET = split[2];
        return String.join("/", Arrays.copyOfRange(split, 3, split.length));

    }
}
