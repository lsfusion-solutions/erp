package lsfusion.erp.integration.image;

import com.google.common.base.Throwables;
import lsfusion.base.file.RawFileData;
import lsfusion.erp.integration.DefaultIntegrationActionProperty;
import lsfusion.utils.type.geo.JsonReader;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.language.ScriptingErrorLog;
import lsfusion.server.language.ScriptingLogicsModule;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultImageArticleActionProperty extends DefaultIntegrationActionProperty {

    public DefaultImageArticleActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImageArticleActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) {
        super(LM, valueClass);
    }

    public DefaultImageArticleActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
    }


    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
    }

    public void loadImages(ExecutionContext context, DataObject articleObject, Integer start, Integer pageSize) {
        try {         
            String url = formatURL(context, articleObject, pageSize, start);
            JSONObject response;
            try {
                response = JsonReader.read(url);
            } catch (IOException e) {
                response = null;
            }
            if (response != null) {
                //может вернуть response status 503
                if (response.get("responseStatus").equals(200)) {
                    JSONArray objectCollection = response.getJSONObject("responseData").getJSONArray("results");

                    for (int i = 0; i < objectCollection.length(); i++) {

                        JSONObject jsonObject = objectCollection.getJSONObject(i);
                        String thumbnailUrl = jsonObject.getString("tbUrl");
                        String imageUrl = jsonObject.getString("url");
                        String width = jsonObject.getString("width");
                        String height = jsonObject.getString("height");

                        File file = readImage(thumbnailUrl);
                        if (file != null && imageUrl != null) {
                            DataObject currentObject = new DataObject(start * pageSize + i);
                            findProperty("thumbnailImage[INTEGER]").change(new RawFileData(file), context, currentObject);
                            findProperty("urlImage[INTEGER]").change(imageUrl, context, currentObject);
                            findProperty("sizeImage[INTEGER]").change(width + "x" + height, context, currentObject);
                            file.delete();
                        }
                    }
                    findProperty("startImage[]").change(start + 1, context);
                    findProperty("articleImage[]").change(articleObject, context);

                    if (start == 0)
                        findAction("chooseImageAction[Article]").execute(context, articleObject);
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public void loadFirstImage(ExecutionContext context, DataObject articleObject) {
        try {
            String url = formatURL(context, articleObject, 8, 0);
            JSONObject response;
            try {
                response = JsonReader.read(url);
            } catch (IOException e) {
                response = null;
            }
            if (response != null) {
                //может вернуть response status 503
                if (response.get("responseStatus").equals(200)) {
                    JSONArray objectCollection = response.getJSONObject("responseData").getJSONArray("results");
                    for (int i = 0; i < 8; i++) {
                        JSONObject jsonObject = objectCollection.getJSONObject(i);
                        File file = readImage(jsonObject.getString("url"));
                        if (file != null) {
                            findProperty("image[Article]").change(new RawFileData(file), context, articleObject);
                            file.delete();
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    protected File readImage(String url) {
        if(url == null) return null;
        File file;
        try {
            URLConnection connection = new URL(url).openConnection();
            InputStream input = connection.getInputStream();
            byte[] buffer = new byte[4096];
            int n;
            file = File.createTempFile("image", ".tmp");
            OutputStream output = new FileOutputStream(file);
            while ((n = input.read(buffer)) != -1) {
                output.write(buffer, 0, n);
            }
            output.close();
        } catch (IOException e) {
            file = null;
        }
        return file;
    }

    private String formatURL(ExecutionContext context, DataObject articleObject, int pageSize, int start) throws ScriptingErrorLog.SemanticErrorException, SQLException, SQLHandledException {
        String patternImageArticle = trim((String) findProperty("patternImage[Article]").read(context, articleObject));
        String idArticle = trim((String) findProperty("id[Article]").read(context, articleObject), "");
        String idBrandArticle = trim((String) findProperty("idBrand[Article]").read(context, articleObject), "");
        String siteBrandArticle = trim((String) findProperty("siteBrand[Article]").read(context, articleObject));
        if (patternImageArticle != null && idArticle.matches(patternImageArticle + ".*")) {
            Pattern p = Pattern.compile(patternImageArticle);
            Matcher m = p.matcher(idArticle);
            if (m.find()) {
                idArticle = m.groupCount() > 0 ? m.group(1) : m.group(0);
            }
        }
        idArticle = idArticle.replace("/", "+");
        String url = "https://ajax.googleapis.com/ajax/services/search/images?v=1.0&q=" +
                idBrandArticle + "%20" + idArticle + "&rsz=" + pageSize + "&start=" + start * pageSize +
                (siteBrandArticle == null ? "" : "&as_sitesearch=" + siteBrandArticle);
        return url.replace(" ", "+");
    }
}