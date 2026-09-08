package com.iitsaii.printagent.downloader;

import com.iitsaii.printagent.config.PrintAgentConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ImageDownloader {

    public Path saveImage(String imageUrl, String sessionId) throws IOException {

        Path hotFolder = Paths.get(PrintAgentConfig.HOT_FOLDER);

        if (Files.notExists(hotFolder)) {
            Files.createDirectories(hotFolder);
        }

        Path output = hotFolder.resolve(sessionId + ".jpg");

        try (InputStream in = new URL(imageUrl).openStream()) {
            Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
        }

        return output;
    }
}
