import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.util.List;

public class ECommerceSystem {
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ecommerce?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "@kavin696"; 
    
    private static Connection connection;
    private static String currentUser = null;
    private static List<Product> products = new ArrayList<>();
    private static List<CartItem> cartItems = new ArrayList<>();

    public static void main(String[] args) {
        
        try {
	    Class.forName("com.mysql.cj.jdbc.Driver"); 
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            initializeDatabase();
            
            
            loadProducts();
            
            
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }  catch (ClassNotFoundException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "MySQL JDBC Driver not found", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to connect to database", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    private static void initializeDatabase() throws SQLException {
        
        try (Statement stmt = connection.createStatement()) {
            
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                         "username VARCHAR(50) PRIMARY KEY, " +
                         "password VARCHAR(50) NOT NULL, " +
                         "email VARCHAR(100) NOT NULL)");
            
            
            stmt.execute("CREATE TABLE IF NOT EXISTS products (" +
                         "id INT PRIMARY KEY, " +
                         "name VARCHAR(100) NOT NULL, " +
                         "category VARCHAR(50) NOT NULL, " +
                         "price DECIMAL(10,2) NOT NULL, " +
                         "stock INT NOT NULL)");
            
            
            stmt.execute("CREATE TABLE IF NOT EXISTS cart (" +
                         "username VARCHAR(50) NOT NULL, " +
                         "product_id INT NOT NULL, " +
                         "quantity INT NOT NULL, " +
                         "PRIMARY KEY (username, product_id), " +
                         "FOREIGN KEY (username) REFERENCES users(username), " +
                         "FOREIGN KEY (product_id) REFERENCES products(id))");
            
            
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM products");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO products VALUES " +
                             "(1, 'Laptop', 'Electronics', 59999, 10), " +
                             "(2, 'Smartphone', 'Electronics', 20000, 15), " +
                             "(3, 'T-Shirt', 'Clothing', 599, 50), " +
                             "(4, 'Jeans', 'Clothing', 799, 30), " +
                             "(5, 'Novel', 'Books', 499, 20), " +
                             "(6, 'Textbook', 'Books', 699, 25)");
            }
        }
    }
    
    private static void loadProducts() {
        products.clear();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM products")) {
            while (rs.next()) {
                products.add(new Product(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("category"),
                    rs.getDouble("price"),
                    rs.getInt("stock")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private static boolean registerUser(String username, String password, String email) {
        try (PreparedStatement pstmt = connection.prepareStatement(
                "INSERT INTO users VALUES (?, ?, ?)")) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, email);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { 
                return false;
            }
            e.printStackTrace();
            return false;
        }
    }
    
    private static boolean authenticateUser(String username, String password) {
        try (PreparedStatement pstmt = connection.prepareStatement(
                "SELECT * FROM users WHERE username = ? AND password = ?")) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                currentUser = username;
                loadCart();
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    private static void addToCart(int productId, int quantity) {
        
        Product product = null;
        for (Product p : products) {
            if (p.getId() == productId && p.getStock() >= quantity) {
                product = p;
                break;
            }
        }
        
        if (product == null) return;
        
        try {
            
            boolean exists = false;
            try (PreparedStatement pstmt = connection.prepareStatement(
                    "SELECT quantity FROM cart WHERE username = ? AND product_id = ?")) {
                pstmt.setString(1, currentUser);
                pstmt.setInt(2, productId);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    exists = true;
                    quantity += rs.getInt("quantity");
                }
            }
            
            if (exists) {
                
                try (PreparedStatement pstmt = connection.prepareStatement(
                        "UPDATE cart SET quantity = ? WHERE username = ? AND product_id = ?")) {
                    pstmt.setInt(1, quantity);
                    pstmt.setString(2, currentUser);
                    pstmt.setInt(3, productId);
                    pstmt.executeUpdate();
                }
            } else {
                
                try (PreparedStatement pstmt = connection.prepareStatement(
                        "INSERT INTO cart VALUES (?, ?, ?)")) {
                    pstmt.setString(1, currentUser);
                    pstmt.setInt(2, productId);
                    pstmt.setInt(3, quantity);
                    pstmt.executeUpdate();
                }
            }
            
            
            loadCart();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private static void loadCart() {
        cartItems.clear();
        if (currentUser == null) return;
        
        try (PreparedStatement pstmt = connection.prepareStatement(
                "SELECT c.product_id, p.name, c.quantity, p.price " +
                "FROM cart c JOIN products p ON c.product_id = p.id " +
                "WHERE c.username = ?")) {
            pstmt.setString(1, currentUser);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                cartItems.add(new CartItem(
                    rs.getInt("product_id"),
                    rs.getString("name"),
                    rs.getInt("quantity"),
                    rs.getDouble("price")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private static void clearCart() {
        if (currentUser == null) return;
        
        try (PreparedStatement pstmt = connection.prepareStatement(
                "DELETE FROM cart WHERE username = ?")) {
            pstmt.setString(1, currentUser);
            pstmt.executeUpdate();
            cartItems.clear();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private static double calculateTotal() {
        double total = 0;
        for (CartItem item : cartItems) {
            total += item.getPrice() * item.getQuantity();
        }
        return total;
    }
    
    
    static class Product {
        private int id;
        private String name;
        private String category;
        private double price;
        private int stock;
        
        public Product(int id, String name, String category, double price, int stock) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.price = price;
            this.stock = stock;
        }
        
        
        public int getId() { return id; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public double getPrice() { return price; }
        public int getStock() { return stock; }
    }
    
    static class CartItem {
        private int productId;
        private String productName;
        private int quantity;
        private double price;
        
        public CartItem(int productId, String productName, int quantity, double price) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.price = price;
        }
        
        
        public int getProductId() { return productId; }
        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public double getPrice() { return price; }
    }
    
    
    static class LoginFrame extends JFrame {
        private JTextField usernameField;
        private JPasswordField passwordField;
        
        public LoginFrame() {
            setTitle("E-Commerce System - Login");
            setSize(350, 250);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            
            JLabel titleLabel = new JLabel("Login to E-Commerce System");
            titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            panel.add(titleLabel, gbc);
            
            gbc.gridwidth = 1;
            gbc.gridy = 1;
            panel.add(new JLabel("Username:"), gbc);
            
            gbc.gridx = 1;
            usernameField = new JTextField(15);
            panel.add(usernameField, gbc);
            
            gbc.gridx = 0;
            gbc.gridy = 2;
            panel.add(new JLabel("Password:"), gbc);
            
            gbc.gridx = 1;
            passwordField = new JPasswordField(15);
            panel.add(passwordField, gbc);
            
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.gridwidth = 2;
            JButton loginButton = new JButton("Login");
            loginButton.addActionListener(e -> attemptLogin());
            panel.add(loginButton, gbc);
            
            gbc.gridy = 4;
            JButton registerButton = new JButton("Register New Account");
            registerButton.addActionListener(e -> {
                new RegistrationFrame().setVisible(true);
                dispose();
            });
            panel.add(registerButton, gbc);
            
            add(panel);
        }
        
        private void attemptLogin() {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter both username and password", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (authenticateUser(username, password)) {
                new MainFrame().setVisible(true);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Invalid username or password", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    static class RegistrationFrame extends JFrame {
        private JTextField usernameField;
        private JPasswordField passwordField;
        private JPasswordField confirmPasswordField;
        private JTextField emailField;
        
        public RegistrationFrame() {
            setTitle("E-Commerce System - Register");
            setSize(400, 300);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setLocationRelativeTo(null);
            
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            
            JLabel titleLabel = new JLabel("Create New Account");
            titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            panel.add(titleLabel, gbc);
            
            gbc.gridwidth = 1;
            gbc.gridy = 1;
            panel.add(new JLabel("Username:"), gbc);
            
            gbc.gridx = 1;
            usernameField = new JTextField(15);
            panel.add(usernameField, gbc);
            
            gbc.gridx = 0;
            gbc.gridy = 2;
            panel.add(new JLabel("Password:"), gbc);
            
            gbc.gridx = 1;
            passwordField = new JPasswordField(15);
            panel.add(passwordField, gbc);
            
            gbc.gridx = 0;
            gbc.gridy = 3;
            panel.add(new JLabel("Confirm Password:"), gbc);
            
            gbc.gridx = 1;
            confirmPasswordField = new JPasswordField(15);
            panel.add(confirmPasswordField, gbc);
            
            gbc.gridx = 0;
            gbc.gridy = 4;
            panel.add(new JLabel("Email:"), gbc);
            
            gbc.gridx = 1;
            emailField = new JTextField(15);
            panel.add(emailField, gbc);
            
            gbc.gridx = 0;
            gbc.gridy = 5;
            gbc.gridwidth = 2;
            JButton registerButton = new JButton("Register");
            registerButton.addActionListener(e -> attemptRegistration());
            panel.add(registerButton, gbc);
            
            gbc.gridy = 6;
            JButton backButton = new JButton("Back to Login");
            backButton.addActionListener(e -> {
                new LoginFrame().setVisible(true);
                dispose();
            });
            panel.add(backButton, gbc);
            
            add(panel);
        }
        
        private void attemptRegistration() {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            String confirmPassword = new String(confirmPasswordField.getPassword());
            String email = emailField.getText().trim();
            
            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || email.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill in all fields", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (!password.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (registerUser(username, password, email)) {
                JOptionPane.showMessageDialog(this, "Registration successful! Please login.", "Success", JOptionPane.INFORMATION_MESSAGE);
                new LoginFrame().setVisible(true);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Username already exists", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    static class MainFrame extends JFrame {
        private JComboBox<String> categoryComboBox;
        private JTextField searchField;
        private JPanel productsPanel;
        private JButton cartButton;
        
        public MainFrame() {
            setTitle("E-Commerce System - Welcome " + currentUser);
            setSize(800, 600);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            
            
            JPanel topPanel = new JPanel(new BorderLayout(10, 10));
            topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            
            Set<String> categories = new TreeSet<>();
            for (Product p : products) {
                categories.add(p.getCategory());
            }
            categoryComboBox = new JComboBox<>();
            categoryComboBox.addItem("All Categories");
            for (String category : categories) {
                categoryComboBox.addItem(category);
            }
            categoryComboBox.addActionListener(e -> filterProducts());
            topPanel.add(categoryComboBox, BorderLayout.WEST);
            
            
            searchField = new JTextField();
            searchField.addActionListener(e -> filterProducts());
            JButton searchButton = new JButton("Search");
            searchButton.addActionListener(e -> filterProducts());
            
            JPanel searchPanel = new JPanel(new BorderLayout());
            searchPanel.add(searchField, BorderLayout.CENTER);
            searchPanel.add(searchButton, BorderLayout.EAST);
            topPanel.add(searchPanel, BorderLayout.CENTER);
            
            
            cartButton = new JButton("Cart (" + cartItems.size() + ")");
            cartButton.addActionListener(e -> {
                new CartFrame().setVisible(true);
            });
            topPanel.add(cartButton, BorderLayout.EAST);
            
            add(topPanel, BorderLayout.NORTH);
            
            
            productsPanel = new JPanel(new GridLayout(0, 3, 10, 10));
            productsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            JScrollPane scrollPane = new JScrollPane(productsPanel);
            add(scrollPane, BorderLayout.CENTER);
            
            
            JButton logoutButton = new JButton("Logout");
            logoutButton.addActionListener(e -> {
                currentUser = null;
                cartItems.clear();
                new LoginFrame().setVisible(true);
                dispose();
            });
            add(logoutButton, BorderLayout.SOUTH);
            
            
            filterProducts();
        }
        
        private void filterProducts() {
            productsPanel.removeAll();
            
            String searchTerm = searchField.getText().toLowerCase();
            String selectedCategory = (String) categoryComboBox.getSelectedItem();
            
            for (Product product : products) {
                if ((selectedCategory.equals("All Categories") || product.getCategory().equals(selectedCategory)) &&
                    (searchTerm.isEmpty() || product.getName().toLowerCase().contains(searchTerm))) {
                    
                    JPanel productPanel = new JPanel(new BorderLayout());
                    productPanel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
                    
                    JLabel nameLabel = new JLabel(product.getName());
                    nameLabel.setFont(new Font("Arial", Font.BOLD, 14));
                    productPanel.add(nameLabel, BorderLayout.NORTH);
                    
                    JLabel detailsLabel = new JLabel(
                        "<html>Category: " + product.getCategory() + "<br>" +
                        "Price: RS" + String.format("%.2f", product.getPrice()) + "<br>" +
                        "Stock: " + product.getStock() + "</html>"
                    );
                    productPanel.add(detailsLabel, BorderLayout.CENTER);
                    
                    JPanel buttonPanel = new JPanel();
                    JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, product.getStock(), 1));
                    buttonPanel.add(quantitySpinner);
                    
                    JButton addButton = new JButton("Add to Cart");
                    addButton.addActionListener(e -> {
                        int quantity = (int) quantitySpinner.getValue();
                        addToCart(product.getId(), quantity);
                        cartButton.setText("Cart (" + cartItems.size() + ")");
                        JOptionPane.showMessageDialog(this, quantity + " " + product.getName() + "(s) added to cart");
                    });
                    buttonPanel.add(addButton);
                    
                    productPanel.add(buttonPanel, BorderLayout.SOUTH);
                    
                    productsPanel.add(productPanel);
                }
            }
            
            productsPanel.revalidate();
            productsPanel.repaint();
        }
    }
    
    static class CartFrame extends JFrame {
        private JTable cartTable;
        private DefaultTableModel tableModel;
        
        public CartFrame() {
            setTitle("Shopping Cart");
            setSize(600, 400);
            setLocationRelativeTo(null);
            
            
            tableModel = new DefaultTableModel(new Object[]{"Product", "Price", "Quantity", "Total"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 2; 
                }
            };
            
            
            for (CartItem item : cartItems) {
                tableModel.addRow(new Object[]{
                    item.getProductName(),
                    String.format("RS%.2f", item.getPrice()),
                    item.getQuantity(),
                    String.format("RS%.2f", item.getPrice() * item.getQuantity())
                });
            }
            
            cartTable = new JTable(tableModel);
            cartTable.getModel().addTableModelListener(e -> {
                if (e.getColumn() == 2) { 
                    int row = e.getFirstRow();
                    try {
                        int newQuantity = Integer.parseInt(tableModel.getValueAt(row, 2).toString());
                        if (newQuantity > 0) {
                            
                            CartItem item = cartItems.get(row);
                            item.setQuantity(newQuantity);
                            
                            
                            try (PreparedStatement pstmt = connection.prepareStatement(
                                    "UPDATE cart SET quantity = ? WHERE username = ? AND product_id = ?")) {
                                pstmt.setInt(1, newQuantity);
                                pstmt.setString(2, currentUser);
                                pstmt.setInt(3, item.getProductId());
                                pstmt.executeUpdate();
                            } catch (SQLException ex) {
                                ex.printStackTrace();
                            }
                            
                            
                            tableModel.setValueAt(
                                String.format("RS%.2f", item.getPrice() * newQuantity),
                                row, 3
                            );
                        }
                    } catch (NumberFormatException ex) {
                        
                        tableModel.setValueAt(cartItems.get(row).getQuantity(), row, 2);
                    }
                }
            });
            
            JScrollPane scrollPane = new JScrollPane(cartTable);
            add(scrollPane, BorderLayout.CENTER);
            
            
            JPanel bottomPanel = new JPanel(new BorderLayout());
            
            JLabel totalLabel = new JLabel("Total: RS" + String.format("%.2f", calculateTotal()));
            totalLabel.setFont(new Font("Arial", Font.BOLD, 16));
            bottomPanel.add(totalLabel, BorderLayout.CENTER);
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            
            JButton checkoutButton = new JButton("Checkout");
            checkoutButton.addActionListener(e -> {
                new BillingFrame().setVisible(true);
                dispose();
            });
            buttonPanel.add(checkoutButton);
            
            JButton removeButton = new JButton("Remove Selected");
            removeButton.addActionListener(e -> {
                int selectedRow = cartTable.getSelectedRow();
                if (selectedRow >= 0) {
                    CartItem item = cartItems.get(selectedRow);
                    
                    
                    try (PreparedStatement pstmt = connection.prepareStatement(
                            "DELETE FROM cart WHERE username = ? AND product_id = ?")) {
                        pstmt.setString(1, currentUser);
                        pstmt.setInt(2, item.getProductId());
                        pstmt.executeUpdate();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                    
                    
                    cartItems.remove(selectedRow);
                    tableModel.removeRow(selectedRow);
                    totalLabel.setText("Total: RS" + String.format("%.2f", calculateTotal()));
                }
            });
            buttonPanel.add(removeButton);
            
            JButton backButton = new JButton("Continue Shopping");
            backButton.addActionListener(e -> dispose());
            buttonPanel.add(backButton);
            
            bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
            
            add(bottomPanel, BorderLayout.SOUTH);
        }
    }
    
    static class BillingFrame extends JFrame {
        public BillingFrame() {
            setTitle("Order Summary");
            setSize(500, 400);
            setLocationRelativeTo(null);
            
            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            
            JTextArea summaryArea = new JTextArea();
            summaryArea.setEditable(false);
            summaryArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            
            StringBuilder summary = new StringBuilder();
            summary.append("================================\n");
            summary.append("        ORDER SUMMARY\n");
            summary.append("================================\n\n");
            summary.append(String.format("%-20s %8s %8s %8s\n", "Product", "Price", "Qty", "Total"));
            
            for (CartItem item : cartItems) {
                summary.append(String.format("%-20s %8.2f %8d %8.2f\n",
                    item.getProductName(),
                    item.getPrice(),
                    item.getQuantity(),
                    item.getPrice() * item.getQuantity()));
            }
            
            summary.append("\n================================\n");
            summary.append(String.format("%20s RS%8.2f\n", "TOTAL:", calculateTotal()));
            summary.append("================================\n");
            summary.append("\nThank you for your purchase!\n");
            
            summaryArea.setText(summary.toString());
            mainPanel.add(new JScrollPane(summaryArea), BorderLayout.CENTER);
            
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            
            JButton printButton = new JButton("Print Receipt");
            printButton.addActionListener(e -> {
                try {
                    summaryArea.print();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error printing receipt", "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            buttonPanel.add(printButton);
            
            JButton doneButton = new JButton("Done");
            doneButton.addActionListener(e -> {
                clearCart();
                dispose();
                JOptionPane.showMessageDialog(this, "Order completed successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            });
            buttonPanel.add(doneButton);
            
            mainPanel.add(buttonPanel, BorderLayout.SOUTH);
            
            add(mainPanel);
        }
    }
}