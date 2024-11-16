/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package analizador;

import static analizador.Tokens.Identificador;
import static analizador.Tokens.Llave_abierta;
import static analizador.Tokens.Llave_cerrada;
import static analizador.Tokens.Salto_Linea;
import static analizador.Tokens.Tipo_dato;
import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java_cup.runtime.Symbol;
import javax.swing.JFileChooser;

/**
 *
 * @author braya
 */
public class Frm1 extends javax.swing.JFrame {

    /**
     * Creates new form Frm1
     */
    
    private String semanticErrors = "";
    private String currentScope = ""; //para almacenar el ambito actual "solo funciona con main"
    HashMap<String, String> declaredVariable = new HashMap<>(); //Para almacenar las variables declaradas
    
    public Frm1() {
        initComponents();
        
        // Establece la ventana en el centro de la pantalla cuando se abre
        this.setLocationRelativeTo(null);
        
        // Hace que el área de texto de resultados ("txtResult") no sea editable
        txtLexicalResult.setEditable(false);
        txtSyntacticResult.setEditable(false);
        txtSemanticResult.setEditable(false);
        txtTranslatorResult.setEditable(false);
    }

    public String translatePythonToC(String pythonCode) {
        StringBuilder cCode = new StringBuilder();
        Set<String> declaredVariables = new HashSet<>();
        Map<String, String> variableTypes = new HashMap<>();
        int indentationLevel = 0;
        boolean usesBoolean = false;
        boolean usesString = false;

        // Headers básicos
        cCode.append("#include <stdio.h>\n");
        cCode.append("#include <stdlib.h>\n");

        // Procesamos el código para detectar tipos especiales
        String[] lines = pythonCode.split("\n");
        for (String line : lines) {
            if (line.contains("True") || line.contains("False")) {
                usesBoolean = true;
            }
            if (line.contains("\"")) {
                usesString = true;
            }
        }

        if (usesBoolean) {
            cCode.append("#include <stdbool.h>\n");
        }
        if (usesString) {
            cCode.append("#include <string.h>\n");
        }

        cCode.append("\nint main() {\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Manejo de comentarios
            if (line.startsWith("#")) {
                cCode.append(getIndentation(indentationLevel)).append("//").append(line.substring(1)).append("\n");
                continue;
            }

            // Manejo de estructuras de control
            if (line.startsWith("if ")) {
                String condition = translateCondition(line.substring(3, line.indexOf(":")));
                cCode.append(getIndentation(indentationLevel)).append("if (").append(condition).append(") {\n");
                indentationLevel++;
            } else if (line.startsWith("elif ")) {
                indentationLevel--;
                String condition = translateCondition(line.substring(5, line.indexOf(":")));
                cCode.append(getIndentation(indentationLevel)).append("} else if (").append(condition).append(") {\n");
                indentationLevel++;
            } else if (line.startsWith("else:")) {
                indentationLevel--;
                cCode.append(getIndentation(indentationLevel)).append("} else {\n");
                indentationLevel++;
            } else if (line.startsWith("for ")) {
                translateForLoop(line, cCode, indentationLevel);
                indentationLevel++;
            } else if (line.startsWith("while ")) {
                String condition = translateCondition(line.substring(6, line.indexOf(":")));
                cCode.append(getIndentation(indentationLevel)).append("while (").append(condition).append(") {\n");
                indentationLevel++;
            } else if (line.startsWith("print(")) {
                translatePrint(line, cCode, indentationLevel, variableTypes);
            } else if (line.contains("=")) {
                translateAssignment(line, cCode, indentationLevel, declaredVariables, variableTypes);
            } else if (line.equals("break")) {
                cCode.append(getIndentation(indentationLevel)).append("break;\n");
            } else if (line.equals("continue")) {
                cCode.append(getIndentation(indentationLevel)).append("continue;\n");
            }
        }

        // Cerrar el main
        cCode.append("\n    return 0;\n}\n");

        return cCode.toString();
    }

    private String getIndentation(int level) {
        return "    ".repeat(level);
    }

    private String translateCondition(String condition) {
        condition = condition.trim();
        condition = condition.replace("and", "&&")
                           .replace("or", "||")
                           .replace("not", "!")
                           .replace("True", "true")
                           .replace("False", "false")
                           .replace("None", "NULL");
        return condition;
    }

    private void translateForLoop(String line, StringBuilder cCode, int indentLevel) {
        String[] parts = line.substring(4, line.indexOf(":")).trim().split(" in ");
        String iterator = parts[0].trim();
        String range = parts[1].trim();

        if (range.startsWith("range(")) {
            String[] rangeParams = range.substring(6, range.length() - 1).split(",");
            String start = rangeParams[0].trim();
            String end = rangeParams.length > 1 ? rangeParams[1].trim() : start;
            String step = rangeParams.length > 2 ? rangeParams[2].trim() : "1";

            if (rangeParams.length == 1) {
                start = "0";
            }

            cCode.append(getIndentation(indentLevel))
                 .append("for (int ").append(iterator).append(" = ").append(start)
                 .append("; ").append(iterator).append(" < ").append(end)
                 .append("; ").append(iterator).append(" += ").append(step).append(") {\n");
        }
    }

    private void translatePrint(String line, StringBuilder cCode, int indentLevel, Map<String, String> varTypes) {
        String content = line.substring(6, line.length() - 1);
        
        if (content.startsWith("\"")) {
            // String literal
            cCode.append(getIndentation(indentLevel)).append("printf(").append(content).append(");\n");
        } else {
            // Variable
            String format = getFormatSpecifier(varTypes.getOrDefault(content, "int"));
            cCode.append(getIndentation(indentLevel))
                 .append("printf(\"").append(format).append("\\n\", ").append(content).append(");\n");
        }
    }

    private String getFormatSpecifier(String type) {
        switch (type) {
            case "int": return "%d";
            case "float": return "%f";
            case "double": return "%lf";
            case "char": return "%c";
            case "char*": return "%s";
            case "bool": return "%d";
            default: return "%d";
        }
    }

    private void translateAssignment(String line, StringBuilder cCode, int indentLevel, 
                                   Set<String> declared, Map<String, String> varTypes) {
        String[] parts = line.split("=");
        String varName = parts[0].trim();
        String value = parts[1].trim();

        String varType = determineType(value);
        varTypes.put(varName, varType);

        if (!declared.contains(varName)) {
            cCode.append(getIndentation(indentLevel))
                 .append(varType).append(" ")
                 .append(varName).append(" = ")
                 .append(translateValue(value, varType)).append(";\n");
            declared.add(varName);
        } else {
            cCode.append(getIndentation(indentLevel))
                 .append(varName).append(" = ")
                 .append(translateValue(value, varType)).append(";\n");
        }
    }

    private String determineType(String value) {
        if (value.equals("True") || value.equals("False")) {
            return "bool";
        } else if (value.startsWith("\"") && value.endsWith("\"")) {
            return "char*";
        } else if (value.contains(".")) {
            return "double";
        } else {
            try {
                Integer.parseInt(value);
                return "int";
            } catch (NumberFormatException e) {
                return "char*";
            }
        }
    }

    private String translateValue(String value, String type) {
        if (type.equals("bool")) {
            return value.equals("True") ? "true" : "false";
        }
        return value;
    }
    
    private void lexicalAnalyze() throws IOException{
        int cont = 1;
        
        String expr = (String) txtInput.getText();
        Lexer lexer = new Lexer(new StringReader(expr));
        String resultado = "   Name " + "\t\tToken\n\n";
        while (true) {
            Tokens token = lexer.yylex();
            if (token == null) {
                txtLexicalResult.setText(resultado);
                return;
            }
            switch (token) {
                case Salto_Linea:
                    cont++;
                    resultado += "-Salto de Linea-\t " + cont + "\n";
                    break;
                case Comillas:
                    resultado += "  -Comillas-\t\t" + lexer.lexeme + "\n";
                    break;
                case Cadena:
                    resultado += "  -Tipo de dato-\t" + lexer.lexeme + "\n";
                    break;
                case Tipo_dato:
                    resultado += "  -Tipo de dato-\t" + lexer.lexeme + "\n";
                    break;
                case If:
                    resultado += "  -Reservada if-\t" + lexer.lexeme + "\n";
                    break;
                case Else:
                    resultado += "  -Reservada else-\t" + lexer.lexeme + "\n";
                    break;
                case Do:
                    resultado += "  -Reservada do-\t" + lexer.lexeme + "\n";
                    break;
                case While:
                    resultado += "  -Reservada while-\t" + lexer.lexeme + "\n";
                    break;
                case For:
                    resultado += "  -Reservada while-\t" + lexer.lexeme + "\n";
                    break;
                case Igual:
                    resultado += "  -Operador igual-\t" + lexer.lexeme + "\n";
                    break;
                case Suma:
                    resultado += "  -Operador suma-\t" + lexer.lexeme + "\n";
                    break;
                case Resta:
                    resultado += "  -Operador resta-\t" + lexer.lexeme + "\n";
                    break;
                case Multiplicacion:
                    resultado += "  -Operador multiplicacion-\t" + lexer.lexeme + "\n";
                    break;
                case Division:
                    resultado += "  -Operador division-\t" + lexer.lexeme + "\n";
                    break;
                case Operador_logico:
                    resultado += "  -Operador logico-\t" + lexer.lexeme + "\n";
                    break;
                case Operador_incremento:
                    resultado += "  -Operador incremento-\t" + lexer.lexeme + "\n";
                    break;
                case Operador_relacional:
                    resultado += "  -Operador relacional-\t" + lexer.lexeme + "\n";
                    break;
                case Operador_atribucion:
                    resultado += "  -Operador atribucion-\t" + lexer.lexeme + "\n";
                    break;
                case Operador_booleano:
                    resultado += "  -Operador booleano-\t" + lexer.lexeme + "\n";
                    break;
                case Parentesis_abierto:
                    resultado += "  -Parentesis de apertura-\t" + lexer.lexeme + "\n";
                    break;
                case Parentesis_cerrado:
                    resultado += "  -Parentesis de cierre-\t" + lexer.lexeme + "\n";
                    break;
                case Llave_abierta:
                    resultado += "  -Llave de apertura-\t" + lexer.lexeme + "\n";
                    break;
                case Llave_cerrada:
                    resultado += "  -Llave de cierre-\t" + lexer.lexeme + "\n";
                    break;
                case Corchete_abierto:
                    resultado += "  -Corchete de apertura-\t" + lexer.lexeme + "\n";
                    break;
                case Corchete_cerrado:
                    resultado += "  -Corchete de cierre-\t" + lexer.lexeme + "\n";
                    break;
                case Main:
                    resultado += "  -Reservada main-\t" + lexer.lexeme + "\n";
                    break;
                case Punto_coma:
                    resultado += "  -Punto y coma-\t" + lexer.lexeme + "\n";
                    break;
                case Identificador:
                    resultado += "  -Identificador-\t" + lexer.lexeme + "\n";
                    break;
                case Numero:
                    resultado += "  -Numero-\t\t" + lexer.lexeme + "\n";
                    break;
                case NumeroDecimal:
                    resultado += "  -NumeroDecimal-\t" + lexer.lexeme + "\n";
                    break;
                case ERROR:
                    resultado += "  -Simbolo no definido-\n";
                    break;
                default:
                    resultado += "   " + lexer.lexeme + " \n";
                    break;
            }
        }
    }
    
    public void syntacticAnalyze() throws IOException {
        String ST = txtInput.getText();
        Sintax s = new Sintax(new analizador.LexerCup(new StringReader(ST)));
        
        try {
            s.parse();
            txtSyntacticResult.setText("Analisis realizado correctamente");
            txtSyntacticResult.setForeground(new Color(25, 111, 61));
        } catch (Exception ex) {
            Symbol sym = s.getS();
            txtSyntacticResult.setText("Error de sintaxis. Linea: " + (sym.right + 1) + " Columna: " + (sym.left + 1) + ", Texto: \"" + sym.value + "\"");
            txtSyntacticResult.setForeground(Color.red);
        }
    }
    
    public void semanticAnalyze() throws IOException{
        currentScope = "";
        semanticErrors = "";
        declaredVariable.clear();
        
        String expr = (String) txtInput.getText();
        Lexer lexer = new Lexer(new StringReader(expr));
        
        boolean errorsFound = false;
        int linea = 1; 
        String currentScope = "otro"; 

        // Análisis léxico y semántico
        while (true) {
            Tokens token = lexer.yylex();
            if (token == null) {
                break;
            }

            switch (token) {
                case Salto_Linea:
                    linea++;
                    break;
                case Tipo_dato:
                    String typeData = lexer.lexeme;
                    Tokens nextToken = lexer.yylex();
                    if (nextToken == Tokens.Identificador) {
                        String identifier = lexer.lexeme;
                        
                        if (currentScope.equals("main")) {
                            if (declaredVariable.containsKey(identifier)) {
                            semanticErrors += " Error: La variable '" + identifier + "' ya ha sido declarada en el mismo ámbito (línea " + linea + ")\n";
                            errorsFound = true;
                            txtSemanticResult.setForeground(Color.red);
                        } else {
                            declaredVariable.put(identifier, typeData);
                            Tokens tokenAsignacion = lexer.yylex();
                            if (tokenAsignacion == Tokens.Igual) {
                                Tokens typeValue = lexer.yylex();
                                if ((typeData.equals("int") && typeValue == Tokens.NumeroDecimal) ||
                                    (typeData.equals("float") && typeValue != Tokens.Numero && typeValue != Tokens.NumeroDecimal) || 
                                        (typeData.equals("double") && typeValue != Tokens.Numero && typeValue != Tokens.NumeroDecimal)){
                                    semanticErrors += " Error: Tipo de dato incorrecto para la variable '" + identifier + "' (línea " + linea + ")\n";
                                    errorsFound = true;
                                    txtSemanticResult.setForeground(Color.red);
                                }
                            }
                        }
                        } else {
                            semanticErrors += " Error: Variable '" + identifier + "' declarada fuera de 'main' (línea " + linea + ")\n";
                            errorsFound = true;
                            txtSemanticResult.setForeground(Color.red);
                        }
                    }
                    break;
                case Identificador:
                    String identifier = lexer.lexeme;
                    // Verificar si la variable no ha sido declarada
                    if (!declaredVariable.containsKey(identifier)) {
                        semanticErrors += " Error: Variable no declarada '" + identifier + "' (línea " + linea + ")\n";
                        errorsFound = true;
                        txtSemanticResult.setForeground(Color.red);
                    } else {
                        Tokens tokenAsignacion = lexer.yylex();
                        // Verificar si hay un valor asignado a la variable y que este sea el corecto
                        if (tokenAsignacion == Tokens.Igual) {
                            String tipoDatoVariable = declaredVariable.get(identifier);
                            Tokens typeValue = lexer.yylex();
                            if ((tipoDatoVariable.equals("int") && typeValue == Tokens.NumeroDecimal) ||
                                (tipoDatoVariable.equals("float") && typeValue != Tokens.Numero && typeValue != Tokens.NumeroDecimal) ||
                                (tipoDatoVariable.equals("double") && typeValue != Tokens.Numero && typeValue != Tokens.NumeroDecimal)) {
                                semanticErrors += " Error: Tipo de dato incorrecto para la variable '" + identifier + "' (línea " + linea + ")\n";
                                errorsFound = true;
                                txtSemanticResult.setForeground(Color.red);
                            }
                        }
                    }
                    break;
                case Llave_abierta:
                    currentScope = "main";
                    break;
                case Llave_cerrada:
                    currentScope = "otro";
                    break;
                default:
                    break;
            }
        }

        // Verificación final de errores y mensajes
         if (!errorsFound) {
            semanticErrors += "No hay errores semánticos. \n";
            txtSemanticResult.setForeground(new Color(25, 111, 61));
        }

         // Mostrar los errores semánticos
        txtSemanticResult.setText(semanticErrors);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        btnClose = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtLexicalResult = new javax.swing.JTextArea();
        jLabel1 = new javax.swing.JLabel();
        btnLexicalAnalyze = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        txtSyntacticResult = new javax.swing.JTextArea();
        jLabel8 = new javax.swing.JLabel();
        btnSyntacticAnalyze = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        jScrollPane5 = new javax.swing.JScrollPane();
        txtSemanticResult = new javax.swing.JTextArea();
        jLabel6 = new javax.swing.JLabel();
        btnSemanticAnalyze = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        txtInput = new javax.swing.JTextArea();
        jLabel5 = new javax.swing.JLabel();
        btnAnalyze = new javax.swing.JButton();
        btnClean = new javax.swing.JButton();
        btnOpenFile = new javax.swing.JButton();
        jPanel6 = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        jScrollPane4 = new javax.swing.JScrollPane();
        txtTranslatorResult = new javax.swing.JTextArea();
        jLabel2 = new javax.swing.JLabel();
        btnTranslate = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setBackground(new java.awt.Color(153, 153, 153));

        jLabel4.setFont(new java.awt.Font("Tempus Sans ITC", 3, 24)); // NOI18N
        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel4.setText("BRAYAN MARTINEZ, 1-20-1136");

        btnClose.setBackground(new java.awt.Color(255, 0, 0));
        btnClose.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        btnClose.setForeground(new java.awt.Color(255, 255, 255));
        btnClose.setText("EXIT");
        btnClose.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        btnClose.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCloseActionPerformed(evt);
            }
        });

        txtLexicalResult.setBackground(new java.awt.Color(204, 204, 204));
        txtLexicalResult.setColumns(20);
        txtLexicalResult.setFont(new java.awt.Font("Times New Roman", 2, 18)); // NOI18N
        txtLexicalResult.setRows(5);
        jScrollPane1.setViewportView(txtLexicalResult);

        jLabel1.setFont(new java.awt.Font("Nirmala UI", 3, 24)); // NOI18N
        jLabel1.setText("Lexical Analyzer");

        btnLexicalAnalyze.setBackground(new java.awt.Color(51, 51, 51));
        btnLexicalAnalyze.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        btnLexicalAnalyze.setForeground(new java.awt.Color(255, 255, 255));
        btnLexicalAnalyze.setText("ANALYZE");
        btnLexicalAnalyze.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        btnLexicalAnalyze.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLexicalAnalyzeActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 193, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnLexicalAnalyze, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(btnLexicalAnalyze))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 267, Short.MAX_VALUE)
                .addContainerGap())
        );

        txtSyntacticResult.setBackground(new java.awt.Color(204, 204, 204));
        txtSyntacticResult.setColumns(20);
        txtSyntacticResult.setFont(new java.awt.Font("Times New Roman", 2, 18)); // NOI18N
        txtSyntacticResult.setRows(5);
        jScrollPane3.setViewportView(txtSyntacticResult);

        jLabel8.setFont(new java.awt.Font("Nirmala UI", 3, 24)); // NOI18N
        jLabel8.setText("Syntactic Analyzer");

        btnSyntacticAnalyze.setBackground(new java.awt.Color(51, 51, 51));
        btnSyntacticAnalyze.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        btnSyntacticAnalyze.setForeground(new java.awt.Color(255, 255, 255));
        btnSyntacticAnalyze.setText("ANALYZE");
        btnSyntacticAnalyze.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        btnSyntacticAnalyze.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSyntacticAnalyzeActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 275, Short.MAX_VALUE)
                        .addComponent(btnSyntacticAnalyze, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(btnSyntacticAnalyze))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 223, Short.MAX_VALUE)
                .addContainerGap())
        );

        txtSemanticResult.setBackground(new java.awt.Color(204, 204, 204));
        txtSemanticResult.setColumns(20);
        txtSemanticResult.setFont(new java.awt.Font("Times New Roman", 2, 18)); // NOI18N
        txtSemanticResult.setRows(5);
        jScrollPane5.setViewportView(txtSemanticResult);

        jLabel6.setFont(new java.awt.Font("Nirmala UI", 3, 24)); // NOI18N
        jLabel6.setText("Semantic Analyzer");

        btnSemanticAnalyze.setBackground(new java.awt.Color(51, 51, 51));
        btnSemanticAnalyze.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        btnSemanticAnalyze.setForeground(new java.awt.Color(255, 255, 255));
        btnSemanticAnalyze.setText("ANALYZE");
        btnSemanticAnalyze.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        btnSemanticAnalyze.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSemanticAnalyzeActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 200, Short.MAX_VALUE)
                        .addComponent(btnSemanticAnalyze, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(btnSemanticAnalyze))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 223, Short.MAX_VALUE)
                .addContainerGap())
        );

        txtInput.setColumns(20);
        txtInput.setFont(new java.awt.Font("Times New Roman", 0, 18)); // NOI18N
        txtInput.setRows(5);
        jScrollPane2.setViewportView(txtInput);

        jLabel5.setFont(new java.awt.Font("Times New Roman", 1, 24)); // NOI18N
        jLabel5.setText("Input:");

        btnAnalyze.setBackground(new java.awt.Color(0, 51, 204));
        btnAnalyze.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        btnAnalyze.setForeground(new java.awt.Color(255, 255, 255));
        btnAnalyze.setText("ANALYZE");
        btnAnalyze.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        btnAnalyze.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAnalyzeActionPerformed(evt);
            }
        });

        btnClean.setBackground(new java.awt.Color(204, 204, 0));
        btnClean.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        btnClean.setForeground(new java.awt.Color(255, 255, 255));
        btnClean.setText("CLEAN");
        btnClean.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        btnClean.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        btnClean.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCleanActionPerformed(evt);
            }
        });

        btnOpenFile.setBackground(new java.awt.Color(153, 153, 153));
        btnOpenFile.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        btnOpenFile.setForeground(new java.awt.Color(255, 255, 255));
        btnOpenFile.setText("OPEN FILE");
        btnOpenFile.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        btnOpenFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOpenFileActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 184, Short.MAX_VALUE)
                        .addComponent(btnOpenFile, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(btnClean, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(btnAnalyze, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(btnAnalyze)
                    .addComponent(btnClean)
                    .addComponent(btnOpenFile))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 271, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(btnClose, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addGap(0, 3, Short.MAX_VALUE)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jPanel5, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(0, 11, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnClose)))
        );

        jPanel6.setBackground(new java.awt.Color(153, 153, 153));

        txtTranslatorResult.setBackground(new java.awt.Color(204, 204, 204));
        txtTranslatorResult.setColumns(20);
        txtTranslatorResult.setFont(new java.awt.Font("Times New Roman", 2, 18)); // NOI18N
        txtTranslatorResult.setRows(5);
        jScrollPane4.setViewportView(txtTranslatorResult);

        jLabel2.setFont(new java.awt.Font("Nirmala UI", 3, 24)); // NOI18N
        jLabel2.setText("Code Translator");

        btnTranslate.setBackground(new java.awt.Color(51, 51, 51));
        btnTranslate.setFont(new java.awt.Font("Times New Roman", 1, 18)); // NOI18N
        btnTranslate.setForeground(new java.awt.Color(255, 255, 255));
        btnTranslate.setText("TRANSLATE");
        btnTranslate.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));
        btnTranslate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTranslateActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 394, Short.MAX_VALUE)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 193, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnTranslate, javax.swing.GroupLayout.PREFERRED_SIZE, 127, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(btnTranslate))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 379, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(205, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnLexicalAnalyzeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLexicalAnalyzeActionPerformed
         try {
            lexicalAnalyze();
        } catch (IOException ex) {
            Logger.getLogger(Frm1.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_btnLexicalAnalyzeActionPerformed

    private void btnCleanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCleanActionPerformed
        // Limpiar el campo de texto de entrada ("txtInput") y el área de resultados ("txtResult")
        txtInput.setText("");
        txtLexicalResult.setText("");
        txtSyntacticResult.setText("");
        txtSemanticResult.setText("");
        txtTranslatorResult.setText("");
    }//GEN-LAST:event_btnCleanActionPerformed

    private void btnCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCloseActionPerformed
        // TODO add your handling code here:
        System.exit(0);
    }//GEN-LAST:event_btnCloseActionPerformed

    private void btnSyntacticAnalyzeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSyntacticAnalyzeActionPerformed
        try {
            syntacticAnalyze();
        } catch (IOException ex) {
            Logger.getLogger(Frm1.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_btnSyntacticAnalyzeActionPerformed

    private void btnOpenFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnOpenFileActionPerformed
        // TODO add your handling code here:
         JFileChooser chooser = new JFileChooser();
        chooser.showOpenDialog(null);
        File archivo = new File(chooser.getSelectedFile().getAbsolutePath());
        
        try {
            String ST = new String(Files.readAllBytes(archivo.toPath()));
            txtInput.setText(ST);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Frm1.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Frm1.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_btnOpenFileActionPerformed

    private void btnAnalyzeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAnalyzeActionPerformed
        // TODO add your handling code here:
         try {
            lexicalAnalyze();
            syntacticAnalyze();
            semanticAnalyze();
        } catch (IOException ex) {
            Logger.getLogger(Frm1.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_btnAnalyzeActionPerformed

    private void btnSemanticAnalyzeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSemanticAnalyzeActionPerformed
        try {
            semanticAnalyze();
        } catch (IOException ex) {
            Logger.getLogger(Frm1.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_btnSemanticAnalyzeActionPerformed

    private void btnTranslateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnTranslateActionPerformed
        String pythonCode = txtInput.getText();
        String cCode = translatePythonToC(pythonCode);
        txtTranslatorResult.setText(cCode);
    }//GEN-LAST:event_btnTranslateActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(Frm1.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(Frm1.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(Frm1.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(Frm1.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Frm1().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAnalyze;
    private javax.swing.JButton btnClean;
    private javax.swing.JButton btnClose;
    private javax.swing.JButton btnLexicalAnalyze;
    private javax.swing.JButton btnOpenFile;
    private javax.swing.JButton btnSemanticAnalyze;
    private javax.swing.JButton btnSyntacticAnalyze;
    private javax.swing.JButton btnTranslate;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JTextArea txtInput;
    private javax.swing.JTextArea txtLexicalResult;
    private javax.swing.JTextArea txtSemanticResult;
    private javax.swing.JTextArea txtSyntacticResult;
    private javax.swing.JTextArea txtTranslatorResult;
    // End of variables declaration//GEN-END:variables
}
