package cn.byyddyh.spoofingdetection.process;

import android.os.Environment;

import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

/**
 * 文件操作类
 *      负责根据指定Url下载到指定文件夹
 *      负责加载指点文件的内容
 */
public class FileUtils {

    private String SDCardRoot;

    public FileUtils() {
        // 得到当前外部存储设备的目录
        // File.separator为文件分隔符”/“,方便之后在目录下创建文件
        // 一般 SDCardRoot:/storage/emulated/0/
        SDCardRoot = Environment.getExternalStorageDirectory() + File.separator;
    }

    /**
     * 在SD卡上创建文件
     */
    public File createFileInSDCard(String fileName, String dir) {
        return new File(SDCardRoot + dir + File.separator + fileName);
    }

    /**
     * 在SD卡上创建目录
     */
    public File createSDDir(String dir) {
        File dirFile = new File(SDCardRoot + dir);
        //mkdir()只能创建一层文件目录，mkdirs()可以创建多层文件目录
        if (dirFile.mkdir()) {
            return dirFile;
        } else {
            return null;
        }
    }

    /**
     * 判断文件是否存在
     */
    public boolean isFileExist(String fileName, String dir) {
        File file = new File(SDCardRoot + dir + File.separator + fileName);
        return file.exists();
    }

    /**
     * 将一个InoutStream里面的数据写入到SD卡中
     */
    public File write2SDFromInput(String fileName, String dir, InputStream input) {
        File file = null;
        OutputStream output = null;
        try {
            //创建目录
            createSDDir(dir);
            //创建文件
            file = createFileInSDCard(fileName, dir);
            //写数据流
            output = new FileOutputStream(file);
            byte[] buffer = new byte[4 * 1024];//每次存4K
            int temp;
            //写入数据
            while ((temp = input.read(buffer)) != -1) {
                output.write(buffer, 0, temp);
            }
            output.flush();
        } catch (Exception e) {
            System.out.println("写数据异常：" + e);
        } finally {
            try {
                assert output != null;
                output.close();
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        return file;
    }

    /**
     * 读取指定文件的内容
     */
    public List<String> loadFileInSDCard(String fileName, String dir) throws IOException {
        InputStream in;
        List<String> lines=new ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            in = new GZIPInputStream(Files.newInputStream(Paths.get(SDCardRoot + dir + File.separator + fileName)));
            Scanner sc=new Scanner(in);
            while(sc.hasNextLine()){
                lines.add(sc.nextLine());
            }
        }

        return lines;
//        List<String> stringList = new ArrayList<>();
//        File file = new File(SDCardRoot + dir + File.separator + fileName);
//        BufferedReader br = null;
//        try {
//            br = new BufferedReader(new FileReader(file));
//            String line = "";
//            while((line = br.readLine())!=null){
//                stringList.add(line);
//            }
//            br.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return stringList;
    }
}
