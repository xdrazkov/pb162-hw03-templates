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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class FSTEngine implements FSTemplateEngine {
    private final Map<String, String> templates = new HashMap<>();

    @Override
    public void loadTemplate(Path file, Charset cs, String ext) {
        String fileName = file.getFileName().toString();
        String templateName = fileName.substring(0, fileName.lastIndexOf('.' + ext));
        String content;
        try {
            content = Files.readString(file, cs);
        } catch (IOException e) {
            throw new TemplateException("Coult not load file.");
        }
        if (isInvalidTemplateString(content)){
            throw new TemplateException("Invalid template");
        }
        templates.put(templateName, content);
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
        if (isInvalidTemplateString(text)){
            throw new TemplateException("Invalid template");
        }
        templates.put(name, text);
    }

    @Override
    public Collection<String> getTemplateNames() {
        return templates.keySet();
    }

    private Token validateInsideParentheses(List<Token> tokens) {
        if (tokens.size() == 1) {
            if (tokens.get(0).getKind() != Token.Kind.NAME
                    && (tokens.get(0).getKind() != Token.Kind.CMD
                    || (!tokens.get(0).cmd().equals("done") && !tokens.get(0).cmd().equals("else")))) {
                return null;
            }
            return tokens.get(0);
        }
        if (tokens.size() == 2) {
            if (tokens.get(0).getKind() != Token.Kind.CMD
                    || !tokens.get(0).cmd().equals("if")
                    || tokens.get(1).getKind() != Token.Kind.NAME) {
                return null;
            }
            return tokens.get(0);
        } else if (tokens.size() == 4) {
            if (tokens.get(0).getKind() != Token.Kind.CMD
                    || !tokens.get(0).cmd().equals("for")
                    || tokens.get(1).getKind() != Token.Kind.NAME
                    || tokens.get(2).getKind() != Token.Kind.IN
                    || tokens.get(3).getKind() != Token.Kind.NAME) {
                return null;
            }
            return tokens.get(0);
        }
        return null;
    }

    /**
     * validates list of tokens
     * @param tokens list of tokens
     * @return true if is valid
     */
    private boolean isValidTemplate(List<Token> tokens) {
        Stack<Token> stack = new Stack<>();
        int index = 0;
        while (index != tokens.size()) {
            Token token = tokens.get(index);
            if (token.getKind() == Token.Kind.OPEN) {
                List<Token> inside = new ArrayList<>();
                while (token.getKind() != Token.Kind.CLOSE) {
                    index++;
                    if (index == tokens.size()) {
                        return false;
                    }
                    token = tokens.get(index);
                    inside.add(token);
                }
                inside.remove(inside.size() - 1);
                Token command = validateInsideParentheses(inside);
                if (command == null) {
                    return false;
                }
                if (command.getKind() == Token.Kind.CMD && (command.cmd().equals("if")
                        || command.cmd().equals("for"))) {
                    stack.add(command);
                } else if (command.getKind() == Token.Kind.CMD && command.cmd().equals("done")) {
                    Token lastCmd = stack.pop();
                    if (lastCmd.getKind() != Token.Kind.CMD
                            || (!lastCmd.cmd().equals("for")
                            && !lastCmd.cmd().equals("if")
                            && !lastCmd.cmd().equals("else"))) {
                        return false;
                    }
                } else if (command.getKind() == Token.Kind.CMD && command.cmd().equals("else")) {
                    Token lastCmd = stack.pop();
                    if (lastCmd.getKind() != Token.Kind.CMD || !lastCmd.cmd().equals("if")) {
                        return false;
                    }
                    stack.add(command);
                }
            }
            index++;
        }
        return stack.isEmpty();
    }

    /**
     * validates template string
     * @param text template string
     * @return true if is valid
     */
    private boolean isInvalidTemplateString(String text) {
        ArrayList<Token> allTokens = new ArrayList<>();
        Tokenizer tokenizer = new Tokenizer(text);
        while (!tokenizer.done()) {
            Token token = tokenizer.consume();
            allTokens.add(token);
        }
        return !isValidTemplate(allTokens);
    }

    /**
     * Gets next token from list of tokens with option to skip parentheses
     * @param tokens list of tokens
     * @param ignoreParentheses whether it should skip parentheses
     * @return next token
     */
    private Token getNextToken(LinkedList<Token> tokens, boolean ignoreParentheses) {
        Token nextToken = tokens.pop();
        if (ignoreParentheses && (nextToken.getKind() == Token.Kind.OPEN || nextToken.getKind() == Token.Kind.CLOSE)) {
            nextToken = tokens.pop();
        }
        return nextToken;
    }

    /**
     * Gets nested block of if or for
     * @param tokens list of tokens
     * @param ifBlock true if it is nested block of if and false if it is nested block of for
     * @return nested block of if or for
     */
    private ArrayList<ArrayList<Token>> getNestedBlock(LinkedList<Token> tokens, boolean ifBlock) {
        ArrayList<ArrayList<Token>> result = new ArrayList<>(Arrays.asList(new ArrayList<>(), new ArrayList<>()));
        ArrayList<Token> addingTo = result.get(0);
        Token currentToken = getNextToken(tokens, true);
        int endCount = 1;
        while (true) {
            if (currentToken.getKind() == Token.Kind.CMD
                    && (currentToken.cmd().equals("for") || currentToken.cmd().equals("if"))) {
                endCount++;
            }
            if (currentToken.getKind() == Token.Kind.CMD && currentToken.cmd().equals("done")) {
                endCount--;
            }
            if (ifBlock && endCount == 1 && currentToken.getKind() == Token.Kind.CMD
                    && currentToken.cmd().equals("else")) {
                addingTo = result.get(1);
            }
            if (endCount == 0) {
                break;
            }
            addingTo.add(currentToken);
            currentToken = getNextToken(tokens, true);
        }
        return result;
    }

    /**
     * Evaluates tokens
     * @param tokens list of tokens
     * @param model model
     * @return evaluated string
     */
    private String evaluateTokens(List<Token> tokens, TemplateModel model) {
        StringBuilder result = new StringBuilder();
        LinkedList<Token> linkedList = new LinkedList<>(tokens);
        while (!linkedList.isEmpty()) {
            Token token = getNextToken(linkedList, false);
            if ((token.getKind() == Token.Kind.OPEN || token.getKind() == Token.Kind.CLOSE) && !linkedList.isEmpty()) {
                token = getNextToken(linkedList, false);
            }
            if (token.getKind() == Token.Kind.TEXT) {
                result.append(token.text());
            } else if (token.getKind() == Token.Kind.NAME) {
                result.append(model.getAsString(token.name()));
            } else if (token.getKind() == Token.Kind.CMD) {
                if (token.cmd().equals("if")) {
                    Token conditionToken = getNextToken(linkedList, true);

                    ArrayList<ArrayList<Token>> bothTokens = getNestedBlock(linkedList, true);
                    ArrayList<Token> ifTokens = bothTokens.get(0);
                    ArrayList<Token> elseTokens = bothTokens.get(1);

                    if (model.getAsBoolean(conditionToken.name())) {
                        result.append(evaluateTokens(ifTokens, model));
                    } else if (!elseTokens.isEmpty()) {
                        result.append(evaluateTokens(elseTokens, model));
                    }

                } else if (token.cmd().equals("for")) {
                    Token newVarToken = getNextToken(linkedList, true);
                    getNextToken(linkedList, true);
                    Token listToken = getNextToken(linkedList, true);

                    String varWas = model.getAsString(newVarToken.name());

                    ArrayList<Token> forTokens = getNestedBlock(linkedList, false).get(0);

                    for (Object element : model.getAsIterable(listToken.name())) {
                        model.put(newVarToken.name(), element.toString());
                        result.append(evaluateTokens(forTokens, model));
                    }

                    model.put(newVarToken.name(), varWas);
                }
            }
        }
        return result.toString();
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
        return evaluateTokens(allTokens, model);
    }
}
