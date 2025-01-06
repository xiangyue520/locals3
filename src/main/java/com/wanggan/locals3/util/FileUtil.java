package com.wanggan.locals3.util;

import com.wanggan.locals3.constant.S3Constant;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class FileUtil {

    public static boolean delete(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            //System.out.println("删除文件失败:" + fileName +"不存在！");
            return false;
        }
        return file.isFile() ? deleteFile(file) : deleteDirectory(fileName);
    }

    public static boolean deleteFile(File file) {
        if (!file.isFile()) {
            return false;
        }
        return file.delete();
    }

    public static boolean deleteDirectory(String dir) {
        if (!dir.endsWith("/"))
            dir = dir + "/";
        File dirFile = new File(dir);
        if ((!dirFile.exists()) || (!dirFile.isDirectory())) {
            return false;
        }
        boolean flag = true;
        File[] files = dirFile.listFiles();
        if (null == files) {
            return true;
        }
        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) {
                flag = deleteFile(files[i]);
                if (!flag)
                    break;
            } else if (files[i].isDirectory()) {
                flag = deleteDirectory(files[i].getAbsolutePath());
                if (!flag)
                    break;
            }
        }
        if (!flag) {
            return false;
        }
        return dirFile.delete();
    }

    public static String getCreationTime(File file) {
        BasicFileAttributes attr = getFileAttributes(file);
        Instant instant = attr.creationTime().toInstant();
        String format = DateUtil.DATE_UTC_FORMATTER.withZone(ZoneId.systemDefault()).format(instant);
        return format;
    }

    private static BasicFileAttributes getFileAttributes(File file) {
        if (file == null) {
            return null;
        }
        try {
            Path path = file.toPath();
            return Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static String getLastModifyTime(File file) {
        BasicFileAttributes attr = getFileAttributes(file);
        Instant instant = attr.lastModifiedTime().toInstant();
        String format = DateUtil.DATE_UTC_FORMATTER.withZone(ZoneId.systemDefault()).format(instant);
        return format;
    }

    public static String getLastModifyTimeGMT(File file) {
        if (file == null) {
            return null;
        }
        Date modifiedTime = new Date(file.lastModified());
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String time = dateFormat.format(modifiedTime);
        return time;
    }

    public static long getFileSize(File file) {
        if (file == null) {
            return 0;
        }
        BasicFileAttributes attr = getFileAttributes(file);
        return attr.size();
    }

    public static void saveFile(String filePath, InputStream fileStream) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(filePath);
            byte[] file = convertStreamToByte(fileStream);
            out.write(file);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (Exception ignored) {

                }
            }
        }
    }

    public static void saveFile(String filePath, byte[] data) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(filePath);
            out.write(data);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static byte[] getFile(String filePath) {
        byte[] buffer = null;
        try {
            File file = new File(filePath);
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1000);
            byte[] b = new byte[1000];
            int n;
            while ((n = fis.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            fis.close();
            bos.close();
            buffer = bos.toByteArray();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    public static byte[] convertStreamToByte(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        bos.close();
        return bos.toByteArray();
    }

    public static void writeFile(String fileName, String content) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(fileName);
            fos.write(content.getBytes(S3Constant.UTF_8));
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static String readFileContent(String fileName) {
        StringBuilder fileContent = new StringBuilder();
        FileInputStream fis = null;
        InputStreamReader isr = null;
        try {
            fis = new FileInputStream(fileName);
            isr = new InputStreamReader(fis, S3Constant.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                fileContent.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (isr != null) {
                    isr.close();
                }
                if (fis != null) {
                    fis.close();
                }
            } catch (Exception ignored) {
            }
        }
        return fileContent.toString();
    }

    public static void copyFile(String sourceFilePath, String targetFilePath) throws IOException {
        try {
            File file = new File(targetFilePath);
            if (file.exists()) {
                file.delete();
            }
            InputStream in = new FileInputStream(sourceFilePath);
            OutputStream out = new FileOutputStream(targetFilePath);
            byte[] buffer = new byte[2048];
            int nBytes;
            while ((nBytes = in.read(buffer)) > 0) {
                out.write(buffer, 0, nBytes);
            }
            out.flush();
            out.close();
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
