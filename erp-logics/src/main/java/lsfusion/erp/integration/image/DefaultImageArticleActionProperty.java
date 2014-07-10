package lsfusion.erp.integration.image;

import com.google.common.base.Throwables;
import lsfusion.base.IOUtils;
import lsfusion.erp.integration.DefaultIntegrationActionProperty;
import lsfusion.erp.utils.geo.JsonReader;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;

public class DefaultImageArticleActionProperty extends DefaultIntegrationActionProperty {

    public DefaultImageArticleActionProperty(ScriptingLogicsModule LM) {
        super(LM);
    }

    public DefaultImageArticleActionProperty(ScriptingLogicsModule LM, ValueClass valueClass) throws ScriptingErrorLog.SemanticErrorException {
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

            String idArticle = trim((String) findProperty("idArticle").read(context, articleObject), "");
            String idBrandArticle = trim((String) findProperty("idBrandArticle").read(context, articleObject), "");
            String siteBrandArticle = trim((String) findProperty("siteBrandArticle").read(context, articleObject));
            String url = "https://ajax.googleapis.com/ajax/services/search/images?v=1.0&q=" + 
                    idBrandArticle + "%20" + idArticle + "&rsz=" + pageSize + "&start=" + start * pageSize + 
                    (siteBrandArticle==null ? "" : "&as_sitesearch=" + siteBrandArticle);

            final JSONObject response = JsonReader.read(url);
            if (response != null) {
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
                        findProperty("thumbnailImage").change(IOUtils.getFileBytes(file), context, currentObject);
                        findProperty("urlImage").change(imageUrl, context, currentObject);
                        findProperty("sizeImage").change(width + "x" + height, context, currentObject);
                        file.delete();
                    }
                }
                findProperty("startImage").change(start + 1, context);
                findProperty("articleImage").change(articleObject, context);

                if (start == 0)
                    findAction("chooseImageAction").execute(context, articleObject);
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
            file = File.createTempFile("image", "tmp");
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
}