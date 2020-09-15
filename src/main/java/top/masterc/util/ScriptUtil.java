package top.masterc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class ScriptUtil {

    private static Logger logger = LoggerFactory.getLogger(ScriptUtil.class);

    /**
     * return lua 脚本
     *
     * @param path
     * @return
     */
    public static String getScript(String path) {
        StringBuilder sb = new StringBuilder();
        InputStream stream = ScriptUtil.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            return null;
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        try {

            String str = "";
            while ((str = br.readLine()) != null) {
                sb.append(str).append(System.lineSeparator());
            }

        } catch (IOException e) {
            logger.error("read script error", e);
        }
        return sb.toString();
    }
}
