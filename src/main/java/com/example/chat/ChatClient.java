package com.example.chat;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Random;

//export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)

public class ChatClient {

    private static String user;
    private static Color userColor;
    private static JTextPane chatPanel;
    private static JTextField inputField;
    private static JButton sendButton;
    private static JButton emojiButton;
    private static JPopupMenu emojiMenu;
    private static Session session;
    private static MessageProducer producer;
    private static MessageConsumer consumer;
    private static Connection connection;

    public static void main(String[] args) {
        user = JOptionPane.showInputDialog("Podaj swoj nick:");
        if (user == null || user.trim().isEmpty()) {
            user = "User"+ new Random().nextInt(1000);
        }


        userColor = JColorChooser.showDialog(null, "Wybierz kolor nicku", Color.BLACK);
        if (userColor == null) {
            userColor = Color.BLACK;
        }

        createAndShowGUI();
        initializeJMS();
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Czat - " + user);
        frame.setSize(500, 500);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        chatPanel = new JTextPane();
        chatPanel.setEditable(false);
        chatPanel.setContentType("text/html");
        chatPanel.setText("<html><head><style>" +
                ".message { margin: 10px; padding: 5px; border: 1px solid #ccc; border-radius: 10px; background-color: #f9f9f9; }" +
                ".message-content { display: block; margin-bottom: 5px; }" +
                ".timestamp { font-size: small; color: #999; text-align: right; }" +
                "</style></head><body></body></html>");
        JScrollPane scrollPane = new JScrollPane(chatPanel);
        frame.add(scrollPane, BorderLayout.CENTER);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        inputField = new JTextField();
        sendButton = new JButton("Wyslij");
        emojiButton = new JButton("Emotki");

        panel.add(inputField, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);
        panel.add(emojiButton, BorderLayout.WEST);

        frame.add(panel, BorderLayout.SOUTH);

        emojiMenu = new JPopupMenu();
        String[] emojis = {":)", ":D", "<3", ":(", ";)", ":P", ":O"};
        for (String emoji : emojis) {
            JMenuItem item = new JMenuItem(emoji);
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    inputField.setText(inputField.getText() + " " + emoji);
                    inputField.requestFocus();
                }
            });
            emojiMenu.add(item);
        }

        emojiButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                emojiMenu.show(emojiButton, emojiButton.getWidth(), emojiButton.getHeight());
            }
        });

        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                sendLeaveMessage();
                try {
                    if (connection != null) {
                        connection.close();
                    }
                } catch (JMSException ex) {
                    ex.printStackTrace();
                }
                System.exit(0);
            }
        });

        frame.setVisible(true);
    }

    private static void initializeJMS() {
        try {
            Properties props = new Properties();
            props.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.exolab.jms.jndi.InitialContextFactory"); // określa klasę fabryki kontekstu JNDI
            props.setProperty(Context.PROVIDER_URL, "tcp://localhost:3035/"); // url dostawcy usług JMS

            Context ctx = new InitialContext(props);

            ConnectionFactory factory = (ConnectionFactory) ctx.lookup("ConnectionFactory"); // Wyszukanie fabryki połączeń JMS za pomocą kontekstu JNDI

            connection = factory.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE); // false - sesja nie jest transakcyjna
            // Session.AUTO_ACKNOWLEDGE - wiadomości będą automatycznie potwierdzane po ich odbiorze
            Destination destination = (Destination) ctx.lookup("topic1"); // wyszukanie celu JMS topic1 za pomocą kontekstu JNDI

            producer = session.createProducer(destination); // tworzenie producenta wiadomości, który będzie wysyłał wiadomości do określonego celu
            consumer = session.createConsumer(destination); // konsument odbierający wiadomości

            consumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    if (message instanceof TextMessage) {
                        try {
                            String text = ((TextMessage) message).getText();
                            appendToPane(chatPanel, text);
                        } catch (JMSException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static void sendMessage() {
        try {
            String text = inputField.getText();
            if (!text.trim().isEmpty()) {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                Color bubbleColor = getRandomColor();
                TextMessage message = session.createTextMessage(
                        "<div class='message' style='background-color: " + toHex(bubbleColor) + ";'>" +
                                "<b style='color: " + toHex(userColor) + ";'>[" + user + "]</b>: " +
                                "<span class='message-content'>" + text + "</span>" +
                                "<div class='timestamp'>" + timestamp + "</div>" +
                                "</div>"
                );
                producer.send(message);
                inputField.setText("");
            }
        } catch (JMSException ex) {
            ex.printStackTrace();
        }
    }

    private static void sendLeaveMessage() {
        try {
            String text = "Uzytkownik " + user + " oposcil chat.";
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            TextMessage message = session.createTextMessage(
                    "<div class='message' style='background-color: #ffcccc;'>" +
                            "<span class='message-content'>" + text + "</span>" +
                            "<div class='timestamp'>" + timestamp + "</div>" +
                            "</div>"
            );
            producer.send(message);
        } catch (JMSException ex) {
            ex.printStackTrace();
        }
    }

    private static void appendToPane(JTextPane tp, String msg) {
        HTMLDocument doc = (HTMLDocument) tp.getDocument();
        HTMLEditorKit editorKit = (HTMLEditorKit) tp.getEditorKit();
        try {
            editorKit.insertHTML(doc, doc.getLength(), msg, 0, 0, null);
            tp.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static Color getRandomColor() {
        Random rand = new Random();
        float r = rand.nextFloat();
        float g = rand.nextFloat();
        float b = rand.nextFloat();
        return new Color(r, g, b);
    }
}
