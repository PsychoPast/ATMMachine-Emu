package com.atm;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.ThreadLocalRandom;

public class MainForm {
    public static void main(String... args) {
        AccountManager.initializeAccounts();
        new ATMFrame();
    }
}

// region ACCOUNT_RELATED_DATA

enum LoginState{
    SUCCESS, // login was successful
    WRONG_PIN, // the user entered a wrong pin
    ACC_NOT_EXIST // an account with the given name does not exist
}

class AccountOwner {
     private final String name; // name of the account holder
     private final String pinCode; // their pin code

     AccountOwner(String name, String pinCode){
         this.name = name;
         this.pinCode = pinCode;
     }

    public String getName() {
        return name;
    }

    public String getPinCode() {
        return pinCode;
    }
}

class UserAccount{
     private final long id; // account id
     private final AccountOwner owner; // account owner
     private double balance; // account balance

    public UserAccount(long id, AccountOwner owner, double balance){
        this.owner = owner;
        this.id = id;
        setBalance(balance);
    }

    public UserAccount(AccountOwner owner, double balance) {
        this(ThreadLocalRandom.current().nextLong(100000000000000L,999999999999999L), owner, balance);

    }

    public long getId() {
        return id;
    }

    public AccountOwner getOwner() {
        return owner;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        if(balance < 0){
            throw new IllegalArgumentException("balance must be a positive number"); // throw an exception in case the balance var is negative
        }

        this.balance = balance;
    }
}

class AccountFetchState{
    private final LoginState state; // login state
    private final UserAccount userAccount; // user account (null if login is unsuccessful, otherwise non-null)

    public AccountFetchState(LoginState state, UserAccount userAccount) {
        this.state = state;
        this.userAccount = userAccount;
    }

    public LoginState getState() {
        return state;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }
}

class AccountManager {
    private static final String ACCOUNT_FILE_LOC = "data\\accounts.bin";
    private static ArrayList<UserAccount> storedAccounts; // stored accounts
    private static boolean hasChangeBeenMade; // a boolean to indicate whether it is necessary to write the accounts to the file

    private AccountManager(){
        hasChangeBeenMade = false;
        storedAccounts = new ArrayList<>();
        try {
            loadAccounts();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Failed to load accounts from file. It might have been deleted or corrupted.", "Load Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    static void initializeAccounts(){
        if(storedAccounts != null){
            throw new IllegalStateException("accounts have already been initialized"); // initialize should only be called once
        }

        new AccountManager();
    }

    static AccountFetchState getUserAccount(long id, String pin){ // we get the account by the id and pin
         LoginState state = LoginState.ACC_NOT_EXIST; // default state in case we don't break from the loop
         UserAccount userAccount = null;
         for(UserAccount user : storedAccounts){ // foreach account in
             if(user.getId() == id){ // we check if the name matches the parameter
                 if(user.getOwner().getPinCode().equals(pin)){ // we then check the pin
                     userAccount = user;
                     state = LoginState.SUCCESS; // if it does, we set the account to the match and set login success
                 }
                 else{
                     state = LoginState.WRONG_PIN; // otherwise, the pin is wrong
                 }

                 break; // acc ids are unique therefore there isn't a possibility to have 2 accounts with the same name
             }
         }

         return new AccountFetchState(state, userAccount); // construct our object with the state and user account
    }

    static long createNewAccount(String name, String pin){
       setHasChangeBeenMade();

        UserAccount newAcc = new UserAccount(new AccountOwner(name, pin), 0.0);
        storedAccounts.add(newAcc); // no need to do any checks here since this method is called only if ::getUserAccount state is ACC_NOT_EXIST
        return newAcc.getId();
    }

    static void saveAllToFile(){ // called upon exiting to save the stored accounts inside a file
        if(hasChangeBeenMade){
            try {
                storeAccounts();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Failed to save accounts to file.", "Save Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    static void setHasChangeBeenMade(){ // notifies the manager that I needs to update the file account
        if(!hasChangeBeenMade){
            hasChangeBeenMade = true;
        }
    }

    private static void loadAccounts() throws IOException {
        BufferedInputStream reader = new BufferedInputStream(new FileInputStream(ACCOUNT_FILE_LOC)); // open the file for reading (throws if the file is not found)
        byte[] buffer = new byte[32]; // allocate a 32 bytes buffer to read from the file into it (which is more than enough)
        reader.read(buffer,0, Integer.BYTES);
        int availableAcc = ByteBuffer.wrap(buffer).getInt(); // we read the first 4 bytes of the file into an int to determine how many accounts are saved
        UserAccount acc;
        int strLength;
        String name, pinCode;
        double accBalance;
        long accId;
        for(int i = 0; i < availableAcc; i++){ // if there are no accounts the for won't execute
            reader.read(buffer,0, Integer.BYTES);
            strLength = ByteBuffer.wrap(buffer).getInt(); // we read from the file the length of the stored string
            reader.read(buffer, 0, strLength); // we read into the buffer strLength bytes
            name = new String(buffer, 0 , strLength); // we construct a new string object from the array
            reader.read(buffer,0,Integer.BYTES); // we read 4 bytes into the buffer representing the pin code
            strLength = ByteBuffer.wrap(buffer).getInt(); // the name of the var is misleading but basically we serialize those 4 bytes into an int (the pin code)
            pinCode = "";
            if(strLength < 1000){ // take for example: 0123. parsing that as an int will give 123 so we need to check if the pin code int is less than 1000 (4 digits) and add a trailing '0' in case it is
                pinCode += '0';
            }

            pinCode += String.valueOf(strLength); // convert the pin to a string
            reader.read(buffer,0, Double.BYTES); // read into the buffer 8 bytes representing the account balance
            accBalance = ByteBuffer.wrap(buffer).getDouble(); // get a double from those 8 bytes
            reader.read(buffer, 0, Long.BYTES); // read into the buffer 8 bytes representing the account id
            accId = ByteBuffer.wrap(buffer).getLong(); // get a long from those 8 bytes

            acc = new UserAccount(accId, new AccountOwner(name, pinCode), accBalance); // we construct a new UserAccount object from the data
            storedAccounts.add(acc); // and add it to the array
        }
    }

    private static void storeAccounts() throws IOException {
        /*
        instead of storing the data as plain text, I took the approach to save it in a binary format (serialize it) in a big-endian order
        I used the concepts of "FString" and "TArray" I worked with in the past from Unreal Engine that basically serializes the size of the array/string before the data itself
        to know how much shall be deserialized when loading the file. This way, I'm able to store all the accounts in a single file using the least amount of space,
        and it provides some layer of security since the pin code and account id are not written in a human-readable format
         */

        // if there is no accounts to save, do nothing
        if(storedAccounts.size() == 0){
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES); // allocate an 8 bytes buffer that is enough to hold int, long and double values at offset 0
        BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(ACCOUNT_FILE_LOC)); // if the file doesn't exist, it will create it
        writer.write(buffer.putInt(0, storedAccounts.size()).array(), 0, Integer.BYTES); // write to the file 4 bytes representing the array length which will be used upon loading the file to determine how many accounts shall be deserialized
        String name;
        int pinCode;
        double accBalance;
        long accId;
        for(UserAccount acc : storedAccounts){ // for each account in the array
            name = acc.getOwner().getName();
            writer.write(buffer.putInt(0, name.length()).array(), 0, Integer.BYTES); // we write to the file the size of the string as a 4 bytes int
            writer.write(name.getBytes(StandardCharsets.UTF_8)); // we write the string itself
            pinCode = Integer.parseInt(acc.getOwner().getPinCode()); // we parse the string into an int
            writer.write(buffer.putInt(0, pinCode).array(), 0, Integer.BYTES); // we store it as 4 bytes
            accBalance = acc.getBalance();
            writer.write(buffer.putDouble(0, accBalance).array(), 0, Double.BYTES); // we store the account balance as 8 bytes
            accId = acc.getId();
            writer.write(buffer.putLong(0, accId).array(), 0, Long.BYTES); // we store the acc id as 8 bytes
        }

        writer.flush(); // flush and close to save our changes
        writer.close();
    }
}

//endregion

// region ASSETS_DATA

class Image{
    private int width; // width of the image
    private int height; // height of the image
    private String resourcePath; // path to the image file

    Image(String path, int width, int height) throws FileNotFoundException { // the constructor will throw an exception in case the file doesn't exist at the given path which will be handled when constructing the static fields
        setWidth(width);
        setHeight(height);
        setResourcePath(path);
    }

    public int getWidth() {
        return width;
    }

    private void setWidth(int width) {
        throwIfNegative(height, "width");
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    private void setHeight(int height) {
        throwIfNegative(height, "height");
        this.height = height;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    private void setResourcePath(String path) throws FileNotFoundException {
        if(!new File(path).exists()){
            throw new FileNotFoundException("File '" + path + "' does not exist therefore the associated image won't get loaded"); // we check if the file exists and throw an exception if it does not
        }

        this.resourcePath = path;
    }

    protected void throwIfNegative(int num, String field){
        if(num < 0){
            throw new IllegalArgumentException(field + " cannot be a negative number"); // we throw an exception if we're given a negative number
        }
    }
}

class Gif extends Image{
    private int duration; // duration of the gif

    Gif(String path, int width, int height, int duration) throws FileNotFoundException {
        super(path, width, height);
        setDuration(duration);
    }

    public int getDuration() {
        return duration;
    }

    private void setDuration(int duration) {
        throwIfNegative(duration, "duration");
        this.duration = duration;
    }
}

class MyColors{ // my custom colors (XX_ALPHA)
    static final Color DARK_GREY_237 = new Color(32, 32, 32, 237);
    static final Color VIRTUAL_SCREEN_NO_ALFA = new Color(60, 70, 92, 0);
    static final Color WHITE_100 = new Color(255,255,255,100);
    static final Color SELECT_TEXT_COLOR = new Color(50, 151, 253);
    static final Color YELLOW_25 = new Color(161, 143, 25);
}

class MyAssets{ // my assets to be used inside the program
    static final Image HEADER_LOGO;
    static final Image LOGO;
    static final Image EXIT;
    static final Image BALANCE_ICON;
    static final Image WITHDRAW_ICON;
    static final Image DEPOSIT_ICON;
    static final Image BACK_ARROW;
    static final Gif SCREEN_TURN_ON_ANIMATION;
    static final Gif SCREEN_TURN_OFF_ANIMATION;
    static final Gif LOADING_SPINNER;
    static final Gif LOADING_SPINNER_RED;

    static {
        Image HEADER_LOGO_TEMP;
        Image LOGO_TEMP;
        Image EXIT_TEMP;
        Image BALANCE_ICON_TEMP;
        Image WITHDRAW_ICON_TEMP;
        Image DEPOSIT_ICON_TEMP;
        Image BACK_ARROW_TEMP;
        Gif SCREEN_TURN_ON_ANIMATION_TEMP;
        Gif SCREEN_TURN_OFF_ANIMATION_TEMP;
        Gif LOADING_SPINNER_TEMP;
        Gif LOADING_SPINNER_RED_TEMP;
        try {
            SCREEN_TURN_ON_ANIMATION_TEMP = new Gif("assets\\welcomeScreen.gif", 350, 350, 2440);
        } catch (FileNotFoundException e) {
            SCREEN_TURN_ON_ANIMATION_TEMP = null;
            JOptionPane.showMessageDialog(null, e.getMessage(), "File Not Found", JOptionPane.ERROR_MESSAGE);
        }
        SCREEN_TURN_ON_ANIMATION = SCREEN_TURN_ON_ANIMATION_TEMP;

        try {
            LOGO_TEMP = new Image("assets\\logo.png", 50, 25);
        } catch (FileNotFoundException e) {
            LOGO_TEMP = null;
            JOptionPane.showMessageDialog(null, e.getMessage(), "File Not Found", JOptionPane.ERROR_MESSAGE);
        }
        LOGO = LOGO_TEMP;

        try {
            HEADER_LOGO_TEMP = new Image("assets\\header.png", 300, 134);
        } catch (FileNotFoundException e) {
            HEADER_LOGO_TEMP = null;
            JOptionPane.showMessageDialog(null, e.getMessage(), "File Not Found", JOptionPane.ERROR_MESSAGE);
        }
        HEADER_LOGO = HEADER_LOGO_TEMP;

        try {
            LOADING_SPINNER_TEMP = new Gif("assets\\loading.gif", 48, 48, 500);
        } catch (FileNotFoundException e) {
            LOADING_SPINNER_TEMP = null;
            JOptionPane.showMessageDialog(null, e.getMessage(), "File Not Found", JOptionPane.ERROR_MESSAGE);
        }
        LOADING_SPINNER = LOADING_SPINNER_TEMP;

        try {
            LOADING_SPINNER_RED_TEMP = new Gif("assets\\loadingRed.gif", 25, 25, 500);
        } catch (FileNotFoundException e) {
            LOADING_SPINNER_RED_TEMP = null;
            JOptionPane.showMessageDialog(null, e.getMessage(), "File Not Found", JOptionPane.ERROR_MESSAGE);
        }
        LOADING_SPINNER_RED = LOADING_SPINNER_RED_TEMP;

        try {
            EXIT_TEMP = new Image("assets\\exit.png", 30, 25);
        } catch (FileNotFoundException e) {
            EXIT_TEMP = null;
            JOptionPane.showMessageDialog(null, e.getMessage(), "File Not Found", JOptionPane.ERROR_MESSAGE);
        }
        EXIT = EXIT_TEMP;

        try {
            SCREEN_TURN_OFF_ANIMATION_TEMP = new Gif("assets\\bye.gif", 350, 350, 3150);
        } catch (FileNotFoundException e) {
            SCREEN_TURN_OFF_ANIMATION_TEMP = null;
            JOptionPane.showMessageDialog(null, e.getMessage(), "File Not Found", JOptionPane.ERROR_MESSAGE);
        }
        SCREEN_TURN_OFF_ANIMATION = SCREEN_TURN_OFF_ANIMATION_TEMP;

        try {
            BALANCE_ICON_TEMP = new Image("assets\\balance.png", 30, 30);
        } catch (FileNotFoundException e) {
            BALANCE_ICON_TEMP = null;
            JOptionPane.showMessageDialog(null, e.getMessage(), "File Not Found", JOptionPane.ERROR_MESSAGE);
        }
        BALANCE_ICON = BALANCE_ICON_TEMP;

        try {
            WITHDRAW_ICON_TEMP = new Image("assets\\withdraw.png", 30, 30);
        } catch (FileNotFoundException e) {
            WITHDRAW_ICON_TEMP = null;
            JOptionPane.showMessageDialog(null, e.getMessage(), "File Not Found", JOptionPane.ERROR_MESSAGE);
        }
        WITHDRAW_ICON = WITHDRAW_ICON_TEMP;

        try {
            DEPOSIT_ICON_TEMP = new Image("assets\\deposit.png", 30, 30);
        } catch (FileNotFoundException e) {
            DEPOSIT_ICON_TEMP = null;
            JOptionPane.showMessageDialog(null, e.getMessage(), "File Not Found", JOptionPane.ERROR_MESSAGE);
        }
        DEPOSIT_ICON = DEPOSIT_ICON_TEMP;

        try {
            BACK_ARROW_TEMP = new Image("assets\\back.png", 40, 40);
        } catch (FileNotFoundException e) {
            BACK_ARROW_TEMP = null;
            JOptionPane.showMessageDialog(null, e.getMessage(), "File Not Found", JOptionPane.ERROR_MESSAGE);
        }
        BACK_ARROW = BACK_ARROW_TEMP;
    }
}

//endregion

// region DELEGATE_INTERFACE

interface IHideEvent {
    void onHide(); // fires when a component goes from visible to hidden
}

class LoginEventArgs { // represents an object that is passed to an onLogin event
    private final UserAccount user; // logged in user
    LoginEventArgs(UserAccount acc){
        if(acc == null){
            throw new IllegalArgumentException("acc cannot be null");
        }

        user = acc;
    }

    public UserAccount getUser() {
        return user;
    }
}

interface IAccountEvent { // represents basic signin/signout events
    void onLogin(LoginEventArgs e);
    void onLogout();
}

abstract class AccountEvent implements IAccountEvent { // since onLogin and onLogout will be used in different contexts,
                                                       // we declare that class that basically implements IAccountEvent and overrides the methods in a way that
                                                       // unless they are overriden in their proper context, attempting to call them will result in an exception being thrown
    @Override
    public void onLogin(LoginEventArgs e) {
        throw new UnsupportedOperationException("pure virtual method has not been overriden");
    }

    @Override
    public void onLogout() {
        throw new UnsupportedOperationException("pure virtual method has not been overriden");
    }
}

interface IRectangleOperation{
    void performOp(Rectangle r);
}

interface ICollapsableAction{
    boolean checkSmaller(int a, int b);
    void performOpOnRectangle(Rectangle r, Rectangle r1, IRectangleOperation action);
    void doTravelDone(Rectangle r, Rectangle r1);
}

interface IInputValidator { // represents an interface that contains methods to determine whether or not all input components are valid
    boolean isValid();
    void doIfValid();
    void doIfInvalid();
    void runValidator();
}

abstract class InputValidator implements IInputValidator {
    public void runValidator(){
        if(isValid()){
            doIfValid();
        }
        else{
            doIfInvalid();
        }
    }
}

interface ITransaction{ // represents a transaction operation
    void onTransaction();
}

// endregion

// region CUSTOM_COMPONENTS

class JImage extends JLabel{
    protected BufferedImage content;
    JImage(Image image, int x, int y){
        if(image != null){
            if(!(this instanceof JGif)){
                try {
                    content = ImageIO.read(new File(image.getResourcePath()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            setBounds(x, y, image.getWidth(), image.getHeight());
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if(content != null){
            callDefaultPaint(g);
            g.drawImage(content, 0, 0, this);
        }
    }

    protected void callDefaultPaint(Graphics g){
        super.paintComponent(g);
    }
}

class JGif extends JImage{
    private IHideEvent hideCallback;
    private int playCount;
    private int duration;
    private boolean isShowing;
    JGif(Gif gif, int x, int y, int playCount) {
        super(gif, x, y);
        if(gif != null){
            setIcon(new ImageIcon(gif.getResourcePath()));
            this.playCount = playCount;
            this.duration = gif.getDuration();
            isShowing = false;
        }
    }

    public void addOnHideEvent(IHideEvent h){
        hideCallback = h;
    }

    public void beginShow(boolean removeFromParent){
        if(getIcon() == null){
            return;
        }

        if(isShowing){
            throw new IllegalStateException("gif is already playing");
        }

        if(getParent() == null){
            throw new RuntimeException("you must add this component to a container");
        }

        if(playCount > 0){
            int timeOut = duration * playCount;
            javax.swing.Timer timer = new javax.swing.Timer(timeOut, e -> {
                if(removeFromParent){
                    getParent().remove(this);
                }
                isShowing = false;
                ((javax.swing.Timer)e.getSource()).stop();
                if(hideCallback != null){
                    hideCallback.onHide();
                }
            });

            isShowing = true;
            repaint();
            timer.start();
        }
        else{
            if(hideCallback != null){
                hideCallback.onHide();
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if(isShowing){
            callDefaultPaint(g);
        }
    }

    @Override
    public boolean isShowing() {
        return isShowing;
    }
}

class JFadingImage extends JImage{
    private float alpha;

    JFadingImage(Image image, int x, int y, float initialAlpha){
        super(image,x , y);
        alpha = initialAlpha;
    }

    public float getAlpha() {
        return alpha;
    }

    public void update(float newAlphaValue){
        if(newAlphaValue < 0.0f || newAlphaValue > 1.0f){
            throw new IllegalArgumentException("alpha value must be [0..1]");
        }

        alpha = newAlphaValue;
        repaint(); // trigger paint component
    }

    @Override
    protected void paintComponent(Graphics g) {
        if(content != null){
            Graphics2D gCopy = (Graphics2D)g.create();
            gCopy.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            super.paintComponent(gCopy);
            gCopy.dispose();
        }
    }
}

enum LoginType {
    SIGN_IN,
    SIGN_UP
}

class InputField extends JTextField{
    private final int characterLimit;
    private boolean isValid;
    private boolean receivedFirstInput;
    private LoginType inputType;
    InputField(int charLimit){
        characterLimit = charLimit;
        isValid = true;
        receivedFirstInput = false;

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if((getText().length() >= characterLimit &&  getSelectedText() == null) || (inputType == LoginType.SIGN_IN && (c < '0' || c > '9'))){
                        e.consume();
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_V) && ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0)) {
                    e.consume();
                    try {
                        String clipBoardData = (String)Toolkit.getDefaultToolkit()
                                .getSystemClipboard().getData(DataFlavor.stringFlavor);

                        int left = charLimit  - getText().length();
                        if(left > 0){
                            String in = clipBoardData.substring(0, Math.min(left, clipBoardData.length()));
                            if(inputType == LoginType.SIGN_IN){
                                try{
                                    Long.parseUnsignedLong(in);
                                }
                                catch(NumberFormatException ex){
                                    return;
                                }
                            }

                            setText(getText() + in);
                        }
                    } catch (UnsupportedFlavorException | IOException ignored) {
                    }
                }
            }
        });

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if(!receivedFirstInput){
                    receivedFirstInput = true;
                }
            }
        });
    }

    public void setType(LoginType type) {
        this.inputType = type;
        setText("");
        receivedFirstInput = false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(MyColors.WHITE_100);
        int fontSize, textY;
        if(getText().isEmpty() && !hasFocus()){
            fontSize = 14;
            textY = (getHeight() / 2) + 5;
        }
        else{
            fontSize = 10;
            textY = 15;
        }

        gCopy.setFont(new Font("sans-serif", Font.BOLD, fontSize));
        gCopy.drawString(inputType == LoginType.SIGN_IN ? "Account Id" : "Username", 20, textY);
        gCopy.dispose();
        getParent().repaint();
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(!isValid ? Color.red : hasFocus() ? MyColors.WHITE_100 : MyColors.DARK_GREY_237);
        gCopy.drawRoundRect(0,0,getWidth() - 1, getHeight() - 1, getHeight() - 1, getHeight() - 1);
        gCopy.dispose();
        getParent().repaint();
    }

    public boolean checkAndUpdate(){
        boolean hasContent = !getText().isEmpty();
        if(inputType == LoginType.SIGN_IN){
            hasContent &= getText().length() == characterLimit;
        }

        isValid = hasContent || !receivedFirstInput;
        return hasContent;
    }
}

class PinInputField extends JPasswordField{
    private char pinChar;
    private boolean isValid;
    private boolean receivedFirstInput;
    PinInputField(){
        isValid = true;
        receivedFirstInput = false;
        pinChar = '\0';
        setCaretColor(new Color(MyColors.VIRTUAL_SCREEN_NO_ALFA.getRed(), MyColors.VIRTUAL_SCREEN_NO_ALFA.getGreen(), MyColors.VIRTUAL_SCREEN_NO_ALFA.getBlue(), 255));
        setHighlighter(null);
        setHorizontalAlignment(JPasswordField.CENTER);
        setFont(new Font("sans-serif", Font.PLAIN, 14));
        setForeground(Color.white);
        setEchoChar('\0');
        getInputMap().put(KeyStroke.getKeyStroke("BACK_SPACE"), "none");
        getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "none");
    }

    public char getPinChar() {
        return pinChar;
    }

    @Override
    public void setText(String t) {
        super.setText(t);
        pinChar = t.charAt(0);
        if(pinChar == '\0'){
            receivedFirstInput = false;
        }
    }

    public boolean checkAndUpdate(){
        boolean hasContent = pinChar != '\0';
        isValid = hasContent || !receivedFirstInput;
        return hasContent;
    }

    public void setInvalid(){
        isValid = false;
        repaint();
    }

    public void addValidatorEvent(Component previousFocusCandidate, Component nextFocusCandidate, IInputValidator validator){
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                e.consume();
            }

            @Override
            public void keyPressed(KeyEvent e) {
                char c = e.getKeyChar();
                if(c >= '0' && c <= '9'){
                    setText(String.valueOf(c));
                    nextFocusCandidate.requestFocus();
                }
                else if(e.getKeyCode() == KeyEvent.VK_RIGHT){
                    nextFocusCandidate.requestFocus();
                }
                else if(e.getKeyCode() == KeyEvent.VK_LEFT){
                    previousFocusCandidate.requestFocus();
                }
            }
        });

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e){
                if(!receivedFirstInput){
                    receivedFirstInput = true;
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                validator.runValidator();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(!isValid ? Color.red : hasFocus() ? MyColors.WHITE_100 : MyColors.DARK_GREY_237);
        gCopy.setStroke(new BasicStroke(1.3f));
        gCopy.drawRoundRect(0,0,getWidth() - 1, getHeight() - 1, getHeight() - 1, getHeight() - 1);
        gCopy.dispose();
        getParent().repaint();
    }
}

class ArrowButton extends JButton{
    ArrowButton(){
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setSelected(true);
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setSelected(false);
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(isSelected() ? Color.yellow : MyColors.YELLOW_25);
        gCopy.setStroke(new BasicStroke(3));
        gCopy.drawLine(2,15,getWidth(),15);
        gCopy.drawLine(2,15,getWidth() - 12,5);
        gCopy.drawLine(2,15,getWidth() - 12,25);
        gCopy.dispose();
        getParent().repaint();
    }

    @Override
    protected void paintBorder(Graphics g) {
    }
}

class LoginButton extends JButton{
    private LoginType actionType;
    LoginButton(){
        setOpaque(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setSelected(true);
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setSelected(false);
                repaint();
            }
        });
    }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setPaint(!isEnabled() ? new Color(253, 239, 119) : isSelected() ? new Color(236, 210, 50, 253) : new GradientPaint(0,0,new Color(161, 143, 25),getWidth()/2,0, new Color(243, 219, 62, 255)));
        gCopy.fillRoundRect(0,0,getWidth() - 1, getHeight() - 1, 15, 15);
        gCopy.setColor(new Color(20, 47, 100));
        gCopy.setFont(new Font("sans-serif", Font.BOLD, 18));
        gCopy.drawString(getText(), (getWidth() - gCopy.getFontMetrics(gCopy.getFont()).stringWidth(getText()))/2, (getHeight() / 2) + 8);
        gCopy.dispose();
        getParent().repaint();
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(hasFocus() ? MyColors.WHITE_100 : MyColors.DARK_GREY_237);
        gCopy.drawRoundRect(0,0,getWidth() - 1, getHeight() - 1, 15, 15);
        gCopy.dispose();
        getParent().repaint();
    }

    public void setType(LoginType type) {
        this.actionType = type;
    }

    @Override
    public void setEnabled(boolean b) {
        setText(!b ? "" : actionType == LoginType.SIGN_IN ? "Log In" : "Register");
        super.setEnabled(b);
    }
}

class GrowingButton extends JButton{
    private boolean hasGrownToFullSize;
    private final int size;
    GrowingButton(int x, int y, int finalW, int finalH){

        Rectangle intial = new Rectangle(x, y, finalW, finalH);
        Rectangle growth = new Rectangle(x - 10,y - 10,finalW + 20, finalH + 20);

        x += (finalW / 2);
        y += (finalH / 2);
        setBounds(x, y,0,0);
        size = finalW;
        hasGrownToFullSize = false;

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if(hasGrownToFullSize){
                    setSelected(true);
                    setBounds(growth);
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if(hasGrownToFullSize){
                    setSelected(false);
                    setBounds(intial);
                    repaint();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(getBackground());
        gCopy.fillRoundRect(0,0,getWidth(), getHeight(), 20, 20);
        gCopy.drawImage(((ImageIcon)getIcon()).getImage(),0,0,30,30,this);
        gCopy.setColor(isSelected() ? new Color(0, 255, 255, 100) : Color.white);
        AffineTransform affineTransform = new AffineTransform();
        affineTransform.rotate(Math.toRadians(-45), 0, 0);
        Font f = getFont().deriveFont(affineTransform).deriveFont(isSelected() ? getFont().getSize() + 2f : getFont().getSize());
        gCopy.setFont(f);
        FontMetrics me = gCopy.getFontMetrics(gCopy.getFont());
        int x,y;
        if(!isSelected()){

            x = (getWidth() - me.stringWidth(getText())) / 2;
            y = (getHeight() - me.getHeight() / 2) - me.getDescent();
        }
        else{
            x = 10 + ((getWidth() - 20) - me.stringWidth(getText())) / 2;
            y = 10 + ((getHeight() - 20) - me.getHeight() / 2) - me.getDescent();
        }

        gCopy.drawString(getText(), x,y);
        gCopy.dispose();
        getParent().repaint();
    }

    @Override
    protected void paintBorder(Graphics g) {
        if(!isSelected()){
            return;
        }

        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setPaint(getBackground());
        gCopy.drawRoundRect(0,0,getWidth() - 1, getHeight() - 1, 20, 20);
        gCopy.dispose();
        getParent().repaint();
    }

    void grow(){
        new javax.swing.Timer(1, e -> {
            Rectangle bounds = getBounds();
            bounds.x -= 1;
            bounds.width += 2;
            bounds.y -= 1;
            bounds.height += 2;
            if(bounds.width < size){
                setBounds(bounds);
                repaint();
            }
            else{
                ((javax.swing.Timer)e.getSource()).stop();
                hasGrownToFullSize = true;
            }
        }).start();
    }
}

class LogoutLabel extends JLabel{
    private IAccountEvent logoutCallback;
    LogoutLabel(){
        super("Logout", JLabel.CENTER);
        setForeground(Color.red);
        setOpaque(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(logoutCallback != null){
                    logoutCallback.onLogout();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setForeground(new Color(229, 49, 49));
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setForeground(Color.red);
                repaint();
            }
        });
    }

    void addLogoutEvent(IAccountEvent event){
        logoutCallback = event;
    }
}

class DisplayLabel extends JLabel{
    private boolean isValid;
    DisplayLabel(String s){
        super(s, JLabel.CENTER);
        isValid = true;

        addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if(evt.getPropertyName() == "text"){
                    if(getText().length() == 1 || getText().charAt(1) == '-'){
                        reset();
                    }
                }
            }
        });
    }

    public void setValid(boolean valid) {
        isValid = valid;
        repaint();
    }

    void reset(){
        setText("$0");
        setValid(true);
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(isValid ? MyColors.DARK_GREY_237: Color.red);
        gCopy.drawRoundRect(0,0,getWidth() - 1, getHeight() - 1, getHeight() - 1, getHeight() - 1);
        gCopy.dispose();
        getParent().repaint();
    }
}

class BackButton extends JButton{
    private DisplayLabel output;
    private boolean pressed = false;
    BackButton(IInputValidator validator){
        setBackground(new Color(128, 24, 24));
        addActionListener(e -> {
            String news = output.getText().substring(0, output.getText().length() - 1);
            output.setText(news);
            validator.runValidator();
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressed = false;
                repaint();
            }
        });
    }

    public void setOutput(DisplayLabel output) {
        this.output = output;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(getBackground());
        gCopy.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
        Image back = MyAssets.BACK_ARROW;
        gCopy.drawImage(new ImageIcon(back.getResourcePath()).getImage(),10,10,back.getWidth(),back.getHeight(),this);
        gCopy.dispose();
        getParent().repaint();
    }

    protected void paintBorder(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(pressed ? getBackground().darker().darker() : getBackground().brighter().brighter());
        gCopy.setStroke(new BasicStroke(3));
        if(pressed){
            gCopy.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 20, 20);
        }
        else {
            gCopy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
        }
        gCopy.dispose();
        getParent().repaint();
    }
}

class SubmitButton extends JButton{
    private boolean pressed = false;
    SubmitButton(){
        setBackground(new Color(13, 70, 13, 255));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressed = false;
            }
        });
    }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(getBackground());
        gCopy.fillRoundRect(0,0,getWidth(),getHeight(),20,20);
        gCopy.dispose();
        getParent().repaint();
    }

    @Override
    protected void paintBorder(Graphics g) {
        if(isEnabled()){
            Graphics2D gCopy = (Graphics2D)g.create();
            gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gCopy.setColor(pressed ? getBackground().darker().darker() : getBackground().brighter().brighter());
            gCopy.setStroke(new BasicStroke(3));
            if(pressed){
                gCopy.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 20, 20);
            }
            else {
                gCopy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            }
            gCopy.dispose();
            getParent().repaint();
        }
    }
}

class BillButton extends JButton{
    private boolean pressed = false;
    BillButton(String s, DisplayLabel output, IInputValidator validator){
        super(s);
        setBackground(MyColors.YELLOW_25);
        setForeground(Color.white);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String aa = output.getText().substring(1);
                if(SwingUtilities.isLeftMouseButton(e)){
                    long a = Long.parseLong(aa) + Integer.parseInt(getText().substring(1));
                    output.setText("$" + a);
                    validator.runValidator();
                }
                else if(SwingUtilities.isRightMouseButton(e)){
                    long a = Long.parseLong(aa) - Integer.parseInt(getText().substring(1));
                    output.setText("$" + a);
                    validator.runValidator();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                setBackground(Color.yellow);
                setForeground(Color.BLACK);
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                setBackground(MyColors.YELLOW_25);
                setForeground(Color.white);
                pressed = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(getBackground());
        gCopy.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
        int x = (getWidth() - getFontMetrics(getFont()).stringWidth(getText())) / 2;
        int y = (getHeight()) / 2;
        gCopy.setColor(getForeground());
        gCopy.drawString(getText(), x, y);
        gCopy.dispose();
        getParent().repaint();
    }

    protected void paintBorder(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(pressed ? getBackground().darker().darker() : getBackground().brighter());
        gCopy.setStroke(new BasicStroke(3));
        if(pressed){
            gCopy.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 20, 20);
        }
        else {
            gCopy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
        }
        gCopy.dispose();
        getParent().repaint();
    }
}

class NumButton extends JButton{
    private boolean pressed = false;
    private final int val;
    private DisplayLabel output;
    private UserAccount user;
    NumButton(String s, IInputValidator validator){
        super(s);
        val = Integer.parseInt(s);
        setBackground(MyColors.DARK_GREY_237);
        setForeground(Color.white);
        addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(output.getText().length() == 18){
                    return;
                }

                String val = output.getText() + getText();

                if(output.getText().length() == 2 && output.getText().charAt(1) == '0'){
                    val = val.charAt(0)  + val.substring(2);
                }
                output.setText(val);
                validator.runValidator();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                setBackground(MyColors.DARK_GREY_237);
                setForeground(Color.BLACK);
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                setBackground(MyColors.DARK_GREY_237);
                setForeground(Color.white);
                pressed = false;
                repaint();
            }
        });
    }

    void bind(DisplayLabel output, UserAccount user){
        setOutput(output);
        setUser(user);
    }

    private void setOutput(DisplayLabel output) {
        this.output = output;
    }

    private void setUser(UserAccount user) {
        this.user = user;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(getBackground());
        gCopy.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
        int x = (getWidth() - getFontMetrics(getFont()).stringWidth(getText())) / 2;
        int y = (getHeight()) / 2;
        gCopy.setColor(Color.white);
        gCopy.drawString(getText(), x, y);
        gCopy.dispose();
        getParent().repaint();
    }

    protected void paintBorder(Graphics g) {
        Graphics2D gCopy = (Graphics2D)g.create();
        gCopy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gCopy.setColor(pressed ? getBackground().darker().darker().darker() : getBackground().brighter().brighter());
        gCopy.setStroke(new BasicStroke(3));
        if(pressed){
            gCopy.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 20, 20);
        }
        else {
            gCopy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
        }
        gCopy.dispose();
        getParent().repaint();
    }
}



class NumPad extends JPanel{
    private DisplayLabel output;
    private UserAccount user;
    private boolean checkAmount;
    private final NumButton num0;
    private final NumButton num1;
    private final NumButton num2;
    private final NumButton num3;
    private final NumButton num4;
    private final NumButton num5;
    private final NumButton num6;
    private final NumButton num7;
    private final NumButton num8;
    private final NumButton num9;
    private final SubmitButton sub;
    private final BackButton back;
    private final IInputValidator validator;
    private ITransaction transactionCallback;

    NumPad(){
        int w, h;
        w = h = 60;
        setLayout(null);

        validator = new InputValidator() {
            @Override
            public boolean isValid() {
                double amount = Double.parseDouble(output.getText().substring(1));
                if(amount == 0){
                    return false;
                }
                if(checkAmount){
                    return !(amount > user.getBalance());
                }

                return true;
            }

            @Override
            public void doIfValid() {
                output.setValid(true);
                sub.setEnabled(true);
            }

            @Override
            public void doIfInvalid() {
                output.setValid(false);
                sub.setEnabled(false);
            }
        };

        back = new BackButton(validator);
        back.setBounds(0,210,w,h);
        sub = new SubmitButton();
        sub.setBounds(140,210,w,h);
        num0 = new NumButton("0", validator);
        num0.setBounds(70, 210, w, h);
        num1 = new NumButton("1", validator);
        num1.setBounds(0, 0, w, h);
        num2 = new NumButton("2", validator);
        num2.setBounds(70, 0, w, h);
        num3 = new NumButton("3", validator);
        num3.setBounds(140, 0, w, h);
        num4 = new NumButton("4", validator);
        num4.setBounds(0, 70, w, h);
        num5 = new NumButton("5", validator);
        num5.setBounds(70, 70, w, h);
        num6 = new NumButton("6", validator);
        num6.setBounds(140, 70, w, h);
        num7 = new NumButton("7", validator);
        num7.setBounds(0, 140, w, h);
        num8 = new NumButton("8", validator);
        num8.setBounds(70, 140, w, h);
        num9 = new NumButton("9", validator);
        num9.setBounds(140, 140, w, h);

        sub.addActionListener(e -> {
            double amount = Double.parseDouble(output.getText().substring(1));
            if(checkAmount){
                user.setBalance(user.getBalance() - amount);
            }
            else{
                user.setBalance(user.getBalance() + amount);
            }

            transactionCallback.onTransaction();
        });

        add(back);
        add(sub);
        add(num0);
        add(num1);
        add(num2);
        add(num3);
        add(num4);
        add(num5);
        add(num6);
        add(num7);
        add(num8);
        add(num9);
    }

    public void bind(DisplayLabel output, UserAccount user, ITransaction onTransaction) {
        this.output = output;
        this.user = user;
        this.transactionCallback = onTransaction;
        num0.bind(output, user);
        num1.bind(output, user);
        num2.bind(output, user);
        num3.bind(output, user);
        num4.bind(output, user);
        num5.bind(output, user);
        num6.bind(output, user);
        num7.bind(output, user);
        num8.bind(output, user);
        num9.bind(output, user);
        back.setOutput(output);
    }

    public void enableChecking(boolean enable){
        checkAmount = enable;
        sub.setEnabled(false);
    }

    public IInputValidator getValidator(){
        return validator;
    }
}

// endregion

// region PANELS

class LoginPanel extends JPanel{
    private final JLabel signInLabel;
    private final InputField userInput;
    private final PinInputField pinInput1;
    private final PinInputField pinInput2;
    private final PinInputField pinInput3;
    private final PinInputField pinInput4;
    private final LoginButton proceedButton;
    private LoginType loginType;
    private JLabel modeSwitcher;
    private final JLabel pinLabel;
    private JGif loadingSpinner;
    private IAccountEvent loginCallback;

    LoginPanel(Rectangle bounds){
        setLayout(null);
        setOpaque(false);
        setBounds(bounds);
        loginType = LoginType.SIGN_IN;

        signInLabel = new JLabel("Sign In", JLabel.CENTER); // sign in label
        signInLabel.setForeground(MyColors.YELLOW_25); // foreground color
        signInLabel.setFont(new Font("sans-serif", Font.BOLD, 50)); // font
        FontMetrics signInLabelFM = signInLabel.getFontMetrics(signInLabel.getFont());
        int signInLabelWidth = signInLabelFM.stringWidth("Sign Up"); // get the width required using the set font (Sign Up is wider than Sign In)
        int signInLabelHeight = signInLabelFM.getHeight(); // get the width required
        int signInLabelX = (getWidth() - signInLabelWidth) / 2; // label x position
        int signInLabelY = 0; // label y position
        signInLabel.setBounds(signInLabelX, signInLabelY, signInLabelWidth, signInLabelHeight); // set label bounds

        final int inputLimit = 15;
        userInput = new InputField(inputLimit); // limit input to 20 characters
        userInput.setOpaque(false); // no opacity
        userInput.setForeground(Color.white); // text color white
        int usernameWidth = 280;
        int usernameHeight = 60;
        int usernameX = (getWidth() - usernameWidth) / 2;
        int usernameY = signInLabelY + signInLabelHeight + 10;
        userInput.setFont(new Font("sans-serif", Font.BOLD, 24)); // text font
        userInput.setBounds(usernameX, usernameY, usernameWidth, usernameHeight); // textbox bounds
        userInput.setHorizontalAlignment(JTextField.CENTER); // set the content to be centered
        userInput.setSelectionColor(MyColors.SELECT_TEXT_COLOR); // set the highlighter color
        userInput.setSelectedTextColor(Color.white); // set highlighted text color
        userInput.setCaretColor(MyColors.WHITE_100); // set caret color
        userInput.setType(loginType);

        final int pinInputWidth = 52;
        final int pinInputHeight = 52;
        int pinInputY = usernameY + 90;
        final int space = pinInputWidth + ((usernameWidth - usernameX) * 5 / 100);

        pinInput1 = new PinInputField();
        pinInput1.setOpaque(false);
        pinInput1.setBounds(usernameX + 20, pinInputY, pinInputWidth, pinInputHeight);

        pinInput2 = new PinInputField();
        pinInput2.setOpaque(false);
        pinInput2.setBounds(pinInput1.getX() + space, pinInputY, pinInputWidth, pinInputHeight);

        pinInput3 = new PinInputField();
        pinInput3.setOpaque(false);
        pinInput3.setBounds(pinInput2.getX() + space, pinInputY, pinInputWidth, pinInputHeight);

        pinInput4 = new PinInputField();
        pinInput4.setOpaque(false);
        pinInput4.setBounds(pinInput3.getX() + space, pinInputY, pinInputWidth, pinInputHeight);

        pinLabel = new JLabel("Pin", JLabel.CENTER);
        pinLabel.setOpaque(false);
        pinLabel.setFont(new Font("sans-serif", Font.BOLD | Font.ITALIC,14));
        pinLabel.setBounds(((usernameWidth - usernameX) / 2) + 12, usernameY + usernameHeight - 10, 80,50);
        setValidPin();

        proceedButton = new LoginButton();
        proceedButton.setBounds(pinInput1.getX() + pinInputWidth / 2,pinInput2.getY() + 65,205,50);
        proceedButton.setEnabled(false);
        proceedButton.setType(loginType);
        proceedButton.addActionListener(e -> {
            proceedButton.setEnabled(false);
            modeSwitcher.setEnabled(false);
            loadingSpinner.beginShow(false);
            repaint();
            requestFocus();
        });

        IInputValidator validator = new InputValidator() {
            @Override
            public boolean isValid() {
                boolean a = userInput.checkAndUpdate();
                boolean b = pinInput1.checkAndUpdate();
                boolean c = pinInput2.checkAndUpdate();
                boolean d = pinInput3.checkAndUpdate();
                boolean e = pinInput4.checkAndUpdate();
                return a && b && c && d && e;
            }

            @Override
            public void doIfValid() {
                if(!loadingSpinner.isShowing()){
                    proceedButton.setEnabled(true);
                }

            }

            @Override
            public void doIfInvalid() {
                proceedButton.setEnabled(false);
            }
        };

        userInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                validator.runValidator();
            }
        });

        pinInput1.addValidatorEvent(userInput, pinInput2, validator);
        pinInput2.addValidatorEvent(pinInput1, pinInput3, validator);
        pinInput3.addValidatorEvent(pinInput2, pinInput4, validator);
        pinInput4.addValidatorEvent(pinInput3, this, validator);

        pinInput1.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handlePinPaste(e, validator);
            }
        });

        pinInput2.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handlePinPaste(e, validator);
            }
        });

        pinInput3.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handlePinPaste(e, validator);
            }
        });

        pinInput4.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handlePinPaste(e, validator);
            }
        });

        modeSwitcher = new JLabel("Register", JLabel.CENTER);
        modeSwitcher.setForeground(MyColors.WHITE_100);
        modeSwitcher.setFont(new Font("sans-serif", Font.BOLD | Font.ITALIC, 10));
        modeSwitcher.setOpaque(false);
        modeSwitcher.setBounds(proceedButton.getX() + (proceedButton.getWidth() - 50) / 2,proceedButton.getY() + 55, 50,15);
        modeSwitcher.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(modeSwitcher.isEnabled()){
                    switchMode();
                    validator.runValidator();
                    requestFocus();
                    repaint();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                modeSwitcher.setForeground(Color.white);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                modeSwitcher.setForeground(MyColors.WHITE_100);
            }
        });

        Gif loading = MyAssets.LOADING_SPINNER;
        int loadingX = (proceedButton.getX() + ((proceedButton.getWidth() - loading.getWidth()) / 2));
        int loadingY = pinInputY + 65;
        loadingSpinner = new JGif(loading, loadingX, loadingY, 5);
        loadingSpinner.addOnHideEvent(()->{
            String content = userInput.getText();
            String pin = "";
            pin += pinInput1.getPinChar();
            pin += pinInput2.getPinChar();
            pin += pinInput3.getPinChar();
            pin += pinInput4.getPinChar();
            if(loginType == LoginType.SIGN_UP){
                long accNb = AccountManager.createNewAccount(content, pin);
                JOptionPane.showMessageDialog(this, "Account created! Your account ID is " + accNb + ". Store it somewhere safe!", "Account Created", JOptionPane.INFORMATION_MESSAGE);
                setLoginMode();
                userInput.setText(String.valueOf(accNb));
                repaint();
            }
            else {
                AccountFetchState state = AccountManager.getUserAccount(Long.parseLong(content), pin);
                LoginState loginState = state.getState();
                if (loginState != LoginState.SUCCESS) {
                    if (loginState == LoginState.ACC_NOT_EXIST) {
                        JOptionPane.showMessageDialog(this, "This account does not exist. If you don't have one, please register.", "Account Not Found", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        setInvalidPin();
                        repaint();
                    }

                    proceedButton.setEnabled(true);
                } else {
                    setValidPin();
                    if (loginCallback != null) {
                        loginCallback.onLogin(new LoginEventArgs(state.getUserAccount()));
                    }
                }
            }

            modeSwitcher.setEnabled(true);
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocus();
            }
        });

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                validator.runValidator();
            }
        });

        add(signInLabel);
        add(userInput);
        add(pinLabel);
        add(pinInput1);
        add(pinInput2);
        add(pinInput3);
        add(pinInput4);
        add(loadingSpinner);
        add(proceedButton);
        add(modeSwitcher);
    }

    void addLoginEvent(IAccountEvent loginEvent){
        loginCallback = loginEvent;
    }

    void reset(){
        userInput.setType(loginType);
        pinInput1.setText("\0");
        pinInput2.setText("\0");
        pinInput3.setText("\0");
        pinInput4.setText("\0");
        repaint();
    }

    private void setInvalidPin(){
        pinLabel.setForeground(Color.red);
        pinLabel.setText("Invalid Pin");
        pinInput1.setInvalid();
        pinInput2.setInvalid();
        pinInput3.setInvalid();
        pinInput4.setInvalid();
    }

    private void setValidPin(){
        pinLabel.setForeground(MyColors.WHITE_100);
        pinLabel.setText("Pin");
    }

    private void switchMode(){
        if(loginType == LoginType.SIGN_IN){
            setSignUpMode();
        }
        else{
            setLoginMode();
        }
    }

    private void setLoginMode(){
        loginType = LoginType.SIGN_IN;
        signInLabel.setText("Sign In");
        userInput.setType(loginType);
        proceedButton.setType(loginType);
        setValidPin();
        modeSwitcher.setText("Register");
    }

    private void setSignUpMode(){
        loginType = LoginType.SIGN_UP;
        signInLabel.setText("Sign Up");
        userInput.setType(loginType);
        proceedButton.setType(loginType);
        setValidPin();
        modeSwitcher.setText("Log In");
    }

    private void handlePinPaste(KeyEvent e, IInputValidator validator){
        if ((e.getKeyCode() == KeyEvent.VK_V) && ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0)) {
            e.consume();
            try {
                String clipBoardData = (String) Toolkit.getDefaultToolkit()
                        .getSystemClipboard().getData(DataFlavor.stringFlavor);
                if(clipBoardData.length() == 4 && Integer.parseInt(clipBoardData) >= 0){
                    pinInput1.setText(String.valueOf(clipBoardData.charAt(0)));
                    pinInput2.setText(String.valueOf(clipBoardData.charAt(1)));
                    pinInput3.setText(String.valueOf(clipBoardData.charAt(2)));
                    pinInput4.setText(String.valueOf(clipBoardData.charAt(3)));
                    validator.runValidator();
                }
            } catch (UnsupportedFlavorException | NumberFormatException | IOException ignored) {
            }
        }
    }
}
enum Operation{
    WITHDRAW,
    DEPOSIT
}

class InterfacePanel extends JPanel{
    private final Component container;
    private final UserAccount user;
    private final JLabel welcomeLabel;
    private final GrowingButton checkBalance;
    private final GrowingButton withdraw;
    private final GrowingButton deposit;
    private final ArrowButton goBack;
    private final JLabel balance;
    private final JLabel opLabel;
    private final DisplayLabel display;
    private Operation op;
    private final BillButton one;
    private final BillButton five;
    private final BillButton ten;
    private final BillButton twenty;
    private final BillButton fifty;
    private final BillButton hundred;
    private final NumPad numpad;
    private final JLabel transactionSuccess;

    InterfacePanel(NumPad n, Rectangle bounds, UserAccount account){
        setLayout(null);
        setOpaque(false);
        setBounds(bounds);
        user = account;
        numpad = n;
        container = n.getParent().getParent().getParent().getParent();

        welcomeLabel = new JLabel("", JLabel.CENTER);
        welcomeLabel.setFont(new Font("sans-serif", Font.BOLD, 20));
        FontMetrics me = welcomeLabel.getFontMetrics(welcomeLabel.getFont());
        int welcomeLabelWidth = me.stringWidth(getWelcomeMessage());
        int welcomeLabelHeight = me.getHeight();
        int welcomeLabelX = (getWidth() - welcomeLabelWidth) / 2;
        welcomeLabel.setBounds(welcomeLabelX, 20, welcomeLabelWidth, welcomeLabelHeight);
        welcomeLabel.setForeground(Color.white);

        int growingButtonWidth, growingButtonHeight;
        growingButtonWidth = growingButtonHeight = 96;
        int growingButtonX = 75;
        int growingButtonY = welcomeLabel.getY() + welcomeLabel.getHeight() + 20;
        int space = 20;
        checkBalance = new GrowingButton(growingButtonX, growingButtonY, growingButtonWidth, growingButtonHeight);
        withdraw = new GrowingButton(growingButtonX + space + growingButtonWidth, growingButtonY, growingButtonWidth, growingButtonHeight);
        deposit = new GrowingButton(growingButtonX + (growingButtonWidth / 2), growingButtonY + space + growingButtonHeight, growingButtonWidth, growingButtonHeight);

        checkBalance.setBackground(new Color(255,0,0, 140));
        withdraw.setBackground(new Color(0,255,0, 140));
        deposit.setBackground(new Color(0,0,255, 140));

        checkBalance.setText("Balance");
        checkBalance.setIcon(new ImageIcon(MyAssets.BALANCE_ICON.getResourcePath()));
        checkBalance.setFont(new Font("sans-serif", Font.BOLD, 20));
        withdraw.setText("Withdraw");
        withdraw.setIcon(new ImageIcon(MyAssets.WITHDRAW_ICON.getResourcePath()));
        withdraw.setFont(new Font("sans-serif", Font.BOLD, 20));
        deposit.setText("Deposit");
        deposit.setIcon(new ImageIcon(MyAssets.DEPOSIT_ICON.getResourcePath()));
        deposit.setFont(new Font("sans-serif", Font.BOLD, 20));
        goBack = new ArrowButton();
        goBack.setBounds(360, getHeight() - 35, 30, 30);

        goBack.addActionListener(e -> {
            goBack.setVisible(false);
            revalidate();
            restore();
            new Timer(1, ev -> {
                Rectangle bb = container.getBounds();
                bb.height -= 10;
                if(bb.height >= 520){
                    container.setBounds(bb);
                    container.repaint();
                }
                else{
                    ((Timer)ev.getSource()).stop();
                }
            }).start();
        });


        balance = new JLabel("", JLabel.CENTER);
        balance.setForeground(MyColors.YELLOW_25);
        balance.setFont(new Font("sans-serif", Font.BOLD, 15));
        balance.setBounds(410,(getHeight() - 250) / 2,250,250);

        opLabel = new JLabel("Withdraw", JLabel.CENTER);
        opLabel.setForeground(MyColors.YELLOW_25);
        opLabel.setFont(new Font("sans-serif", Font.BOLD, 30));
        opLabel.setBounds(450,0,150,75);

        display = new DisplayLabel("$0");
        display.setForeground(MyColors.WHITE_100);
        display.setOpaque(false);
        display.setFont(new Font("sans-serif", Font.BOLD, 24));
        display.setBounds(opLabel.getX() - 50,70,250,40);

        IInputValidator val = n.getValidator();

        one = new BillButton("$1", display, val);
        one.setBounds(380 + (350 - 250) / 2,130,60,60);
        five = new BillButton("$5", display, val);
        five.setBounds(450 + (350 - 250) / 2,130,60,60);
        ten = new BillButton("$10", display, val);
        ten.setBounds(520 + (350 - 250) / 2,130,60,60);
        twenty = new BillButton("$20", display, val);
        twenty.setBounds(380 + (350 - 250) / 2,200,60,60);
        fifty = new BillButton("$50", display, val);
        fifty.setBounds(450+ (350 - 250) / 2,200,60,60);
        hundred = new BillButton("$100", display, val);
        hundred.setBounds(520 + (350 - 250) / 2,200,60,60);

        transactionSuccess = new JLabel("Transaction Success", JLabel.CENTER);
        transactionSuccess.setForeground(MyColors.YELLOW_25);
        transactionSuccess.setFont(new Font("sans-serif", Font.BOLD, 30));
        transactionSuccess.setBounds(350,(getHeight() - 250) / 2,350,250);
        transactionSuccess.setVisible(false);

        n.bind(display, user, () ->{
            AccountManager.setHasChangeBeenMade();
            hideWithAndDep();
            transactionSuccess.setVisible(true);
            revalidate();
            repaint();
           new javax.swing.Timer(1000,e->{
               goBack.doClick();
               transactionSuccess.setVisible(false);
               ((Timer)e.getSource()).stop();
           }).start();
        });

        checkBalance.addActionListener(e -> {
            hideWithAndDep();
            showBalanceMode();
            goBack.setVisible(true);
            expand();
        });

        deposit.addActionListener(e -> {
            hideBalanceMode();
            op = Operation.DEPOSIT;
            switchOp();
        });

        withdraw.addActionListener(e -> {
            hideBalanceMode();
            op = Operation.WITHDRAW;
            switchOp();
        });

        add(transactionSuccess);
        add(welcomeLabel);
        add(checkBalance);
        add(withdraw);
        add(deposit);
        add(goBack);
        Rectangle b = getBounds();
        b.width *=2;
        setBounds(b);
        add(balance);
        add(opLabel);
        add(display);
        add(one);
        add(five);
        add(ten);
        add(twenty);
        add(fifty);
        add(hundred);
        repaint();
    }

    void expand(){
         new javax.swing.Timer(1, e->{
             Rectangle bounds = getBounds();
             bounds.x -= 10;
             if(bounds.x >= -350){
                 setBounds(bounds);
                 getParent().repaint();
             }
             else{
                 ((javax.swing.Timer)e.getSource()).stop();
             }
         }).start();
    }

    void restore(){
        new javax.swing.Timer(1, e->{
            Rectangle bounds = getBounds();
            bounds.x += 10;
            if(bounds.x <= 0){
                setBounds(bounds);
                getParent().repaint();
            }
            else{
                ((javax.swing.Timer)e.getSource()).stop();
            }
        }).start();
    }

    void displayWelcomeAnimation(){
        new javax.swing.Timer(100, new ActionListener() {
            private final String str = getWelcomeMessage();
            private final int length = str.length();
            private int index = 0;

            @Override
            public void actionPerformed(ActionEvent e) {
                if(index == length){
                    ((javax.swing.Timer)e.getSource()).stop();
                    checkBalance.grow();
                    withdraw.grow();
                    deposit.grow();
                }
                else{
                    welcomeLabel.setText(welcomeLabel.getText() + str.charAt(index++));
                    getParent().repaint();
                }
            }
        }).start();
    }

    private String getWelcomeMessage(){
        return "Welcome, " + user.getOwner().getName();
    }

    private void hideBalanceMode(){
        setBalanceVisibility(false);
    }

    private void showBalanceMode(){
        setBalanceVisibility(true);
        balance.setText("Balance: $" + user.getBalance());
        repaint();
    }

    private void setBalanceVisibility(boolean isVisible){
        balance.setVisible(isVisible);
        revalidate();
        repaint();
    }

    private void hideWithAndDep(){
        setWithAndDepVisibility(false);
    }

    private void showWithAndDep(){
        setWithAndDepVisibility(true);
    }

    private void setWithAndDepVisibility(boolean isVisible){
        opLabel.setVisible(isVisible);
        display.setVisible(isVisible);
        one.setVisible(isVisible);
        five.setVisible(isVisible);
        ten.setVisible(isVisible);
        twenty.setVisible(isVisible);
        fifty.setVisible(isVisible);
        hundred.setVisible(isVisible);

        revalidate();
        repaint();
    }

    private void switchOp(){
        if(op == Operation.DEPOSIT){
            opLabel.setText("Deposit");
            numpad.enableChecking(false);
        }
        else{
            opLabel.setText("Withdraw");
            numpad.enableChecking(true);
        }

        display.reset();
        showWithAndDep();
        goBack.setVisible(true);
        expand();
        new javax.swing.Timer(1, ev -> {
            Rectangle bb = container.getBounds();
            bb.height += 10;
            if(bb.height <= 810){
                container.setBounds(bb);
                container.repaint();
            }
            else{
                ((javax.swing.Timer)ev.getSource()).stop();
            }
        }).start();
    }
}

// endregion

class ATMFrame extends JFrame {
    private static final int FRAME_WIDTH = 700;
    private static final int FRAME_HEIGHT = 810;

    private final JImage headerLogo;
    private final JFadingImage screenLogo;
    private final JGif startupScreen;
    private final JPanel virtualScreen;
    private final JLabel dateLabel;
    private final JLabel cardSlot;
    private LoginPanel loginPanel;
    private final JLabel idLabel;
    private final LogoutLabel logOutLabel;
    private final JGif logoutSpinner;
    private JFadingImage exitIcon;
    private InterfacePanel interfacePanel;
    private final NumPad numpad;

    ATMFrame(){
        Dimension screenResolution = Toolkit.getDefaultToolkit().getScreenSize(); // get screen resolution

        int winX = (screenResolution.width - FRAME_WIDTH) / 2; // where to position the frame (middle of the screen)
        int winY = (screenResolution.height - FRAME_HEIGHT) / 2; // where to position the frame (middle of the screen)

        setUndecorated(true); // no decoration
        setLayout(null); // use no layout
        setResizable(false); // can't be resized
        setBounds(winX, winY, FRAME_WIDTH, 520); // set the frame bounds
        Color backCol = MyColors.DARK_GREY_237; // color of the frame background
        setBackground(backCol);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        Image headerImage = MyAssets.HEADER_LOGO;
        headerLogo = new JImage(headerImage, 10, 10); // logo at the top of the frame

        virtualScreen = new JPanel(); // inner screen
        virtualScreen.setLayout(null); // no layout
        virtualScreen.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(BevelBorder.LOWERED, backCol.brighter().brighter(), backCol.brighter(), backCol.darker().darker(), backCol.darker()),
                BorderFactory.createBevelBorder(BevelBorder.RAISED, backCol.brighter().brighter(), backCol.brighter(), backCol.darker().darker(), backCol.darker()))
        ); // set the virtual screen borders to give it some depth

        int virtualScreenWidth, virtualScreenHeight;
        virtualScreenWidth = virtualScreenHeight = FRAME_WIDTH * 50 / 100; // we want the inner virtual screen to be a square even thought the frame is not
        int virtualScreenX = (FRAME_WIDTH - virtualScreenWidth) / 2; // position the virtual screen in the middle on the signInLabelX axis
        int virtualScreenY = headerImage.getHeight() + 10; // position the virtual screen under the header image on the Y axis
        virtualScreen.setBounds(virtualScreenX, virtualScreenY, virtualScreenWidth, virtualScreenHeight); // set its bounds
        virtualScreen.setBackground(MyColors.VIRTUAL_SCREEN_NO_ALFA); // sets its background

        cardSlot = new JLabel(){
            @Override
            protected void paintComponent(Graphics g) { // setBackground doesn't seem to work so we paint the component ourselves with a shade of white
                Graphics2D gCopy = (Graphics2D) g.create(); // we must not modify g
                gCopy.setColor(MyColors.WHITE_100);
                gCopy.fillRect(0, 0, getWidth(), getHeight()); // fill the rectangle
                gCopy.dispose(); // dispose of our copy
                getParent().repaint(); // force the "parent" aka frame to repaint the component
            }
        };

        numpad = new NumPad();
        numpad.setOpaque(false);
        numpad.setBounds(virtualScreenX + 80, 520, 210,280);

        cardSlot.setBorder(virtualScreen.getBorder()); // set the same border to get some depth effect
        int cardSlotWidth = FRAME_WIDTH * 30 / 100;
        int cardSlotHeight = 10;
        int cardSlotX = (FRAME_WIDTH - cardSlotWidth) / 2;
        int cardSlotY = virtualScreenY + virtualScreenHeight + 15;
        cardSlot.setBounds(cardSlotX, cardSlotY, cardSlotWidth, cardSlotHeight);

        Image logo = MyAssets.LOGO; // logo on top of the inner screen
        int logoX = (virtualScreenWidth - logo.getWidth()) / 2; // place it in the middle on the Y axis and 10 pixels down the Y axis
        screenLogo = new JFadingImage(MyAssets.LOGO, logoX, 10, 0); // set initial alfa to 0 to give it a fading effect using a swing timer
        startupScreen = new JGif(MyAssets.SCREEN_TURN_ON_ANIMATION, 0, 0, 1); // set the "turn on" screen animation gif to cover the whole inner screen and play once

        dateLabel = new JLabel("", JLabel.CENTER); // date label that constantly displays the current time
        dateLabel.setFont(new Font("Calibri", Font.BOLD, 12)); // set the date font
        dateLabel.setForeground(MyColors.WHITE_100); // set the data content color
        final SimpleDateFormat datePattern = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss a"); // date pattern format to display
        FontMetrics dateMetrics = dateLabel.getFontMetrics(dateLabel.getFont());
        int requiredWidth = dateMetrics.stringWidth("00/00/0000 00:00:00 AM"); // get the width required to display any datetime using the set font
        int requiredHeight = dateMetrics.getHeight(); // get the height required to display any datetime using the set font
        int dateLabelX = virtualScreenWidth - 10 - requiredWidth; // set the label 10 pixel on the left of the right-end of the inner screen
        dateLabel.setBounds(dateLabelX, 10, requiredWidth , requiredHeight); // set the label bounds

        idLabel = new JLabel("", JLabel.LEFT);
        idLabel.setForeground(dateLabel.getForeground());
        idLabel.setFont(dateLabel.getFont());
        idLabel.setBounds(10, dateLabel.getY(), dateLabel.getWidth(), dateLabel.getHeight());

        logOutLabel = new LogoutLabel();
        int logOutLabelWidth = 37;
        int logOutLabelHeight = 17;
        int logOutLabelX = idLabel.getX() + ((idLabel.getWidth() - logOutLabelWidth) / 3);
        logOutLabel.setBounds(logOutLabelX, dateLabel.getY() + idLabel.getHeight(), logOutLabelWidth, logOutLabelHeight);
        logOutLabel.setFont(dateLabel.getFont());
        logOutLabel.addLogoutEvent(new AccountEvent() {
            @Override
            public void onLogout() {
                virtualScreen.remove(logOutLabel);
                revalidate();
                repaint();
                logoutSpinner.beginShow(false);
            }
        });

        logoutSpinner = new JGif(MyAssets.LOADING_SPINNER_RED, logOutLabel.getX(), logOutLabel.getY(), 4);
        logoutSpinner.addOnHideEvent(()->{
            virtualScreen.remove(interfacePanel);
            virtualScreen.remove(idLabel);
            virtualScreen.add(exitIcon);
            virtualScreen.add(loginPanel);
            revalidate();
            repaint();
            loginPanel.reset();
            loginPanel.requestFocus();
        });

        int panelY = screenLogo.getY() + screenLogo.getHeight() + 10; // panel y position
        createLoginPanel(new Rectangle(0, panelY, virtualScreenWidth, virtualScreenHeight - panelY));

        virtualScreen.add(startupScreen);
        exitIcon = new JFadingImage(MyAssets.EXIT,0,0, 0.5f);
        exitIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                 JGif bye = new JGif(MyAssets.SCREEN_TURN_OFF_ANIMATION, 0, 0,1 );
                 bye.addOnHideEvent(()->{
                     exit();
                 });

                 virtualScreen.removeAll();
                 virtualScreen.add(bye);
                 revalidate();
                 repaint();
                 bye.beginShow(true);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                exitIcon.update(1.0f);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                exitIcon.update(0.5f);
            }
        });

        add(headerLogo);
        add(virtualScreen);
        add(numpad);

        setVisible(true);

        startupScreen.addOnHideEvent(()->{
            virtualScreen.add(screenLogo);
            revalidate();
            repaint();
            new javax.swing.Timer(1, e -> {
                Color currentBg = virtualScreen.getBackground();
                int alpha = currentBg.getAlpha() + 1;
                if(alpha > 255){
                    ((javax.swing.Timer) e.getSource()).stop();
                    virtualScreen.add(dateLabel);
                    virtualScreen.add(logoutSpinner);
                    virtualScreen.add(exitIcon);
                    virtualScreen.add(loginPanel);
                    repaint();
                    javax.swing.Timer timer = new javax.swing.Timer(1000, ev -> {
                        dateLabel.setText(datePattern.format(Calendar.getInstance().getTime()));
                        repaint();
                    });

                    timer.setInitialDelay(0);
                    timer.start();
                }
                else{
                    virtualScreen.setBackground(new Color(currentBg.getRed(), currentBg.getGreen(), currentBg.getBlue(), alpha));
                    screenLogo.update(screenLogo.getAlpha() + 0.002f);
                    repaint();
                }
            }).start();
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                AccountManager.saveAllToFile();
            }
        });

        startupScreen.beginShow(true);
    }

    private void createLoginPanel(Rectangle bounds){
        loginPanel = new LoginPanel(bounds);
        loginPanel.addLoginEvent(new AccountEvent() {
            @Override
            public void onLogin(LoginEventArgs e) {
                idLabel.setText("ID: " + e.getUser().getId());
                interfacePanel = new InterfacePanel(numpad, loginPanel.getBounds(), e.getUser());
                virtualScreen.remove(loginPanel);
                virtualScreen.remove(exitIcon);
                virtualScreen.add(idLabel);
                virtualScreen.add(logOutLabel);
                virtualScreen.add(interfacePanel);
                revalidate();
                repaint();
                interfacePanel.displayWelcomeAnimation();
            }
        });
    }

    private void exit(){
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }
}