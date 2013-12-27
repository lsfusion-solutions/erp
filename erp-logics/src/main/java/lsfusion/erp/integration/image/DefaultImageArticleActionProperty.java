package lsfusion.erp.integration.image;

import lsfusion.base.IOUtils;
import lsfusion.erp.integration.DefaultIntegrationActionProperty;
import lsfusion.erp.utils.geo.JsonReader;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;
import org.json.JSONArray;
import org.json.JSONException;
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
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
    }

    public void loadImages(ExecutionContext context, DataObject articleObject, Integer start, Integer pageSize) {

        try {

            String idArticle = trim((String) getLCP("idArticle").read(context, articleObject));
            idArticle = idArticle == null ? "" : idArticle;
            String idBrandArticle = trim((String) getLCP("idBrandArticle").read(context, articleObject));
            idBrandArticle = idBrandArticle == null ? "" : idBrandArticle;
            String url = "https://ajax.googleapis.com/ajax/services/search/images?v=1.0&q=" + idBrandArticle + "%20" + idArticle + "&rsz=" + pageSize + "&start=" + start * pageSize;

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
                        getLCP("thumbnailImage").change(IOUtils.getFileBytes(file), context, currentObject);
                        getLCP("urlImage").change(imageUrl, context, currentObject);
                        getLCP("sizeImage").change(width + "x" + height, context, currentObject);
                    }
                }
                getLCP("startImage").change(start + 1, context);
                getLCP("articleImage").change(articleObject, context);

                if (start == 0)
                    getLAP("chooseImageAction").execute(context, articleObject);
            }
        } catch (ScriptingErrorLog.SemanticErrorException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected File readImage(String url) {
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