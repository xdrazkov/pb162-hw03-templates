package cz.muni.fi.pb162.hw03.impl;

import cz.muni.fi.pb162.hw03.impl.parser.tokens.Token;
import cz.muni.fi.pb162.hw03.impl.parser.tokens.Tokenizer;
import cz.muni.fi.pb162.hw03.template.FSTemplateEngine;
import cz.muni.fi.pb162.hw03.template.TemplateException;
import cz.muni.fi.pb162.hw03.template.model.TemplateModel;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class FSTEngine implements FSTemplateEngine {
    private final Map<String, String> templates = new HashMap<>();
    private TemplateParser parser;

    @Override
    public void loadTemplate(Path file, Charset cs, String ext) {
        String fileName = file.getFileName().toString();
        String templateName = fileName.substring(0, fileName.lastIndexOf('.' + ext));
        String content;
        try {
            content = Files.readString(file, cs);
        } catch (IOException e) {
            throw new TemplateException("Could not load file.");
        }
        loadTemplate(templateName, content);
    }

    @Override
    public void loadTemplateDir(Path inDir, Charset cs, String ext) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inDir)) {
            for (Path file : stream) {
                loadTemplate(file, cs, ext);
            }
        } catch (IOException e) {
            throw new TemplateException("Could not load dir.");
        }
    }

    @Override
    public void writeTemplate(String name, TemplateModel model, Path file, Charset cs) {
        try {
            Files.writeString(file, evaluateTemplate(name, model), cs);
        } catch (IOException e) {
            throw new TemplateException("Error writing to file: " + e.getMessage(), e);
        }
    }

    @Override
    public void writeTemplates(TemplateModel model, Path outDir, Charset cs) {
        for (Map.Entry<String, String> template : templates.entrySet()) {
            writeTemplate(template.getKey(), model, Path.of(String.valueOf(outDir), template.getKey()), cs);
        }
    }

    @Override
    public void loadTemplate(String name, String text) {
        parser = new TemplateParser();
        parser.isInvalidTemplateString(text);
        templates.put(name, text);
    }

    @Override
    public Collection<String> getTemplateNames() {
        return templates.keySet();
    }



    @Override
    public String evaluateTemplate(String name, TemplateModel model) {
        if (templates.get(name) == null) {
            throw new TemplateException("Template does not exist.");
        }

        ArrayList<Token> allTokens = new ArrayList<>();

        Tokenizer tokenizer = new Tokenizer(templates.get(name));
        while (!tokenizer.done()) {
            Token token = tokenizer.consume();
            allTokens.add(token);
        }
        return parser.evaluateTokens(allTokens, model);
    }
}
