package cz.muni.fi.pb162.hw03.impl;

import cz.muni.fi.pb162.hw03.impl.parser.tokens.Token;
import cz.muni.fi.pb162.hw03.impl.parser.tokens.Tokenizer;
import cz.muni.fi.pb162.hw03.template.TemplateException;
import cz.muni.fi.pb162.hw03.template.model.TemplateModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

public class TemplateParser {
    private Token validateInsideParentheses(List<Token> tokens) {
        if (tokens.size() != 1 && tokens.size() != 2 && tokens.size() != 4) {
            throw new TemplateException("Invalid template.");
        }
        if ((tokens.size() == 1) && (tokens.get(0).getKind() != Token.Kind.NAME
                && (tokens.get(0).getKind() != Token.Kind.CMD
                || (!tokens.get(0).cmd().equals("done") && !tokens.get(0).cmd().equals("else"))))) {
            throw new TemplateException("Invalid template.");
        }
        if ((tokens.size() == 2) && (tokens.get(0).getKind() != Token.Kind.CMD
                || !tokens.get(0).cmd().equals("if")
                || tokens.get(1).getKind() != Token.Kind.NAME)) {
            throw new TemplateException("Invalid template.");
        }
        if ((tokens.size() == 4) && (tokens.get(0).getKind() != Token.Kind.CMD
                || !tokens.get(0).cmd().equals("for")
                || tokens.get(1).getKind() != Token.Kind.NAME
                || tokens.get(2).getKind() != Token.Kind.IN
                || tokens.get(3).getKind() != Token.Kind.NAME)) {
            throw new TemplateException("Invalid template.");
        }
        return tokens.get(0);
    }

    private int getUntilClose(Token token, int index, List<Token> inside, List<Token> tokens) {
        while (token.getKind() != Token.Kind.CLOSE) {
            index++;
            if (index == tokens.size()) {
                throw new TemplateException("Invalid template.");
            }
            token = tokens.get(index);
            inside.add(token);
        }
        inside.remove(inside.size() - 1);
        return index;
    }

    private int validateOpenToken(List<Token> tokens, Stack<Token> stack, Token token, int index) {
        List<Token> inside = new ArrayList<>();
        index = getUntilClose(token, index, inside, tokens);
        Token command = validateInsideParentheses(inside);
        if (command.getKind() == Token.Kind.CMD && (command.cmd().equals("if")
                || command.cmd().equals("for"))) {
            stack.add(command);
        } else if (command.getKind() == Token.Kind.CMD && command.cmd().equals("done")) {
            stack.pop();
        } else if (command.getKind() == Token.Kind.CMD && command.cmd().equals("else")) {
            Token lastCmd = stack.pop();
            if (lastCmd.getKind() != Token.Kind.CMD || !lastCmd.cmd().equals("if")) {
                throw new TemplateException("Invalid template.");
            }
            stack.add(command);
        }
        return index;
    }

    /**
     * validates list of tokens
     * @param tokens list of tokens
     */
    private void isValidTemplate(List<Token> tokens) {
        Stack<Token> stack = new Stack<>();
        int index = 0;
        while (index != tokens.size()) {
            Token token = tokens.get(index);
            if (token.getKind() == Token.Kind.OPEN) {
                index = validateOpenToken(tokens, stack, token, index);
            }
            index++;
        }
        if (!stack.isEmpty()) {
            throw new TemplateException("Invalid template.");
        }
    }

    /**
     * validates template string
     * @param text template string
     */
    public void isInvalidTemplateString(String text) {
        ArrayList<Token> allTokens = new ArrayList<>();
        Tokenizer tokenizer = new Tokenizer(text);
        while (!tokenizer.done()) {
            Token token = tokenizer.consume();
            allTokens.add(token);
        }
        isValidTemplate(allTokens);
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
     * Gets nested block of if
     * @param tokens list of tokens
     * @return nested block of for
     */
    private ArrayList<ArrayList<Token>> getNestedBlockIf(LinkedList<Token> tokens) {
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
            if (endCount == 1 && currentToken.getKind() == Token.Kind.CMD
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
     * Gets nested block of for
     * @param tokens list of tokens
     * @return nested block of or
     */
    private ArrayList<ArrayList<Token>> getNestedBlockFor(LinkedList<Token> tokens) {
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
            if (endCount == 0) {
                break;
            }
            addingTo.add(currentToken);
            currentToken = getNextToken(tokens, true);
        }
        return result;
    }

    private void evaluateIf(LinkedList<Token> linkedList, TemplateModel model, StringBuilder result) {
        Token conditionToken = getNextToken(linkedList, true);

        ArrayList<ArrayList<Token>> bothTokens = getNestedBlockIf(linkedList);
        ArrayList<Token> ifTokens = bothTokens.get(0);
        ArrayList<Token> elseTokens = bothTokens.get(1);

        if (model.getAsBoolean(conditionToken.name())) {
            result.append(evaluateTokens(ifTokens, model));
        } else if (!elseTokens.isEmpty()) {
            result.append(evaluateTokens(elseTokens, model));
        }
    }

    private void evaluateFor(LinkedList<Token> linkedList, TemplateModel model, StringBuilder result) {
        Token newVarToken = getNextToken(linkedList, true);
        getNextToken(linkedList, true);
        Token listToken = getNextToken(linkedList, true);

        String varWas = model.getAsString(newVarToken.name());

        ArrayList<Token> forTokens = getNestedBlockFor(linkedList).get(0);

        for (Object element : model.getAsIterable(listToken.name())) {
            model.put(newVarToken.name(), element.toString());
            result.append(evaluateTokens(forTokens, model));
        }

        model.put(newVarToken.name(), varWas);
    }

    /**
     * Evaluates tokens
     * @param tokens list of tokens
     * @param model model
     * @return evaluated string
     */
    public String evaluateTokens(List<Token> tokens, TemplateModel model) {
        StringBuilder result = new StringBuilder();
        LinkedList<Token> linkedList = new LinkedList<>(tokens);
        while (!linkedList.isEmpty()) {
            Token token = getNextToken(linkedList, false);
            if (token.getKind() == Token.Kind.TEXT) {
                result.append(token.text());
            } else if (token.getKind() == Token.Kind.NAME) {
                result.append(model.getAsString(token.name()));
            } else if (token.getKind() == Token.Kind.CMD && token.cmd().equals("if")) {
                evaluateIf(linkedList, model, result);
            } else if (token.getKind() == Token.Kind.CMD && token.cmd().equals("for")) {
                evaluateFor(linkedList, model, result);
            }
        }
        return result.toString();
    }

}
