import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class Main {

    public static void main(String[] args) {

        ArrayList<PreparedPosts> preparedPostsArrayList = new ArrayList<>();

        String newPosterDir = "";
        String dbPath = "";
        String tableName = "";
        String httpsProxyHost = "";
        String httpsProxyPort = "";

        File postJson = new File("default.json");

        try {
            Scanner s = new Scanner(postJson);
            StringBuilder builder = new StringBuilder();

            while (s.hasNextLine()) builder.append(s.nextLine());

            JSONParser pars = new JSONParser();

            try {
                Object obj = pars.parse(builder.toString());
                JSONObject overallConfig = (JSONObject) obj;

                newPosterDir = (String) overallConfig.get("newPosterDir");
                dbPath = (String) overallConfig.get("dbPath");
                tableName = (String) overallConfig.get("tableName");
                httpsProxyHost = (String) overallConfig.get("httpsProxyHost");
                httpsProxyPort = (String) overallConfig.get("httpsProxyPort");


            } catch (ParseException e) {
                e.printStackTrace();
            }

            s.close();
        }catch (Exception e){
            e.printStackTrace();
        }

        LogSave conn = new LogSave(dbPath, tableName);

        try{
            conn.conn();
            conn.CreateDB();
        }catch (Exception e){
            e.printStackTrace();
        }

        String blogtype = "";
        String blogname = "";
        String key = "";
        String tags = "";
        String charset = "UTF-8";
        String currentPostTags = "";
        List<String> serverHashPhoto;
        String docUploadedFileResult;
        String uploadPicPage = "";
        String uploadDocPage = "";
        String multipleAttachments = "";

        File directory = new File(newPosterDir);
        File[] fList = directory.listFiles();

        for (File fileToProcess : fList) {
            File defaultConfigJson = new File(fileToProcess.toString()+"//post.json");

            try {
                Scanner s = new Scanner(defaultConfigJson);
                StringBuilder builder = new StringBuilder();

                while (s.hasNextLine()) builder.append(s.nextLine());

                JSONParser pars = new JSONParser();

                try {
                    Object obj = pars.parse(builder.toString());
                    JSONObject overallConfig = (JSONObject) obj;

                    blogtype = (String) overallConfig.get("blogtype");
                    blogname = (String) overallConfig.get("blogname");
                    key = (String) overallConfig.get("key");
                    tags = (String) overallConfig.get("tags");

                } catch (ParseException e) {
                    e.printStackTrace();
                }

                preparedPostsArrayList.add(new PreparedPosts(fileToProcess.getName(), blogtype, blogname, key, tags));

                s.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //--------------------------------------------------------------------------------------------------------------

        try {
            conn.WriteEventToDB("Post count", "info", Integer.toString(fList.length));
        } catch (Exception z) {
            z.printStackTrace();
        }

        for (PreparedPosts dirToProcess : preparedPostsArrayList) {

            dirToProcess.showAll();

            File tempDirectory = new File(newPosterDir + "//" + dirToProcess.getDirectory());
            File[] tempFileList = tempDirectory.listFiles();


            Boolean savePhotoResult = false;

            switch(dirToProcess.getBlogType()) {
                case "vkontakte": case "NEWvkontakte":
                    if (httpsProxyHost != null && httpsProxyPort != null){
                        System.setProperty("https.proxyHost", httpsProxyHost);
                        System.setProperty("https.proxyPort", httpsProxyPort);
                    }
                    multipleAttachments = "";

                    try {
                        uploadPicPage = VkHelper.getWallUploadServer(dirToProcess.getKey(), dirToProcess.getBlogname());
                    }catch (Exception e) {
                        e.printStackTrace();
                        try {
                            //Write getWallUploadServer error
                            conn.WriteEventToDB("getWallUploadServer", "error", dirToProcess.getBlogType() + ":" + e.getMessage());
                        } catch (Exception z) {
                            z.printStackTrace();
                        }
                    }

                    try {
                        uploadDocPage = VkHelper.docsGetWallUploadServer(dirToProcess.getKey(), dirToProcess.getBlogname());
                    }catch (Exception e) {
                        e.printStackTrace();
                        try {
                            //Write getWallUploadServer error
                            conn.WriteEventToDB("docsGetWallUploadServer", "error", dirToProcess.getDirectory() + ":" + e.getMessage());
                        } catch (Exception z) {
                            System.out.println("docsGetWallUploadServer " + "error" + dirToProcess.getDirectory() + ":" + e.getMessage());
                            z.printStackTrace();
                        }
                    }

                    for (File fileToProcess : tempFileList) {

                        //System.out.println("fileToProcess: " + fileToProcess);

                        try {
                            switch (FilenameUtils.getExtension(fileToProcess.toString()).toLowerCase()) {
                                case "jpg": case "jpeg":case "png":
                                    serverHashPhoto = VkHelper.uploadPhoto(uploadPicPage, charset, fileToProcess);
                                    multipleAttachments = multipleAttachments + VkHelper.saveWallPhoto(dirToProcess.getKey(), dirToProcess.getBlogname(), serverHashPhoto.get(0), serverHashPhoto.get(1), serverHashPhoto.get(2));
                                    break;
                                case "gif":
                                    docUploadedFileResult = VkHelper.uploadDoc(uploadDocPage, charset, fileToProcess);
                                    multipleAttachments = multipleAttachments + VkHelper.docsSave(dirToProcess.getKey(), docUploadedFileResult);
                                    break;
                            }
                        }catch(Exception e){
                            try {
                                //Write uploadPhoto error
                                conn.WriteEventToDB("uploadPhoto and uploadDoc", "error", e.getMessage());
                            } catch (Exception z) {
                                z.printStackTrace();
                            }
                        }

                    }
                    if (multipleAttachments.length() > 0){
                        try{
                            savePhotoResult = VkHelper.postWallPhoto(dirToProcess.getKey(), dirToProcess.getBlogname(), multipleAttachments,  URLEncoder.encode(dirToProcess.getTags(), charset));
                        }catch (Exception e) {
                            e.printStackTrace();

                            try {
                                //Write postWallPhoto error
                                conn.WriteEventToDB("postWallPhoto", "error", e.getMessage());
                            } catch (Exception z) {
                                z.printStackTrace();
                            }
                        }
                    }else{
                        try {
                            //Write postWallPhoto error
                            conn.WriteEventToDB("postWallPhoto", "error", "multipleAttachments length < 0");
                        } catch (Exception z) {
                            z.printStackTrace();
                        }
                    }

                    if (httpsProxyHost != null && httpsProxyPort != null){
                        System.setProperty("https.proxyHost", "");
                        System.setProperty("https.proxyPort", "");
                    }
                    break;
                case "NEWtumblr": case "tumblr":
                    try{
                        for (File fileToProcess : tempFileList) {
                            switch (FilenameUtils.getExtension(fileToProcess.toString()).toLowerCase()) {
                                case "jpg": case "jpeg": case "png": case "gif":
                                    savePhotoResult = TumblrHelper.postSinglePhoto(
                                            dirToProcess.getBlogname(),
                                            dirToProcess.getTags(),
                                            //токен тумблра состоит из 4рех кусков, которые я склеил вместе чтобы не усложнять конфиг
                                            dirToProcess.getKey().substring(0, 50),
                                            dirToProcess.getKey().substring(50, 100),
                                            dirToProcess.getKey().substring(100, 150),
                                            dirToProcess.getKey().substring(150, 200),
                                            fileToProcess);

                                    System.out.println("savePhotoResult: " + savePhotoResult);
                                    break;
                            }
                        }
                    }catch(Exception tpe){
                        tpe.printStackTrace();
                        try {
                            //tumblr upload
                            conn.WriteEventToDB("tumblr postSinglePhoto", "error", dirToProcess.getDirectory() + "_" + tpe.getMessage());
                        } catch (Exception z) {
                            z.printStackTrace();
                        }
                    }
                    break;
            }

            if (savePhotoResult){
                try {
                    FileUtils.deleteDirectory(tempDirectory);
                }catch (IOException ioe){
                    try {
                        conn.WriteEventToDB("deleteDirectory", "error",tempDirectory.toString() + "_" + ioe.getMessage());
                    } catch (Exception z) {
                        z.printStackTrace();
                    }
                    ioe.printStackTrace();
                }

                try {
                    conn.WriteEventToDB("Post send success", "info", dirToProcess.getBlogname() + " " + dirToProcess.getBlogname());
                } catch (Exception z) {
                    z.printStackTrace();
                }

            }
        }




    }
}
