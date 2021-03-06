// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package codeu.controller;

import codeu.model.data.Conversation;
import codeu.model.data.Message;
import codeu.model.data.User;
import codeu.model.store.basic.ConversationStore;
import codeu.model.store.basic.MessageStore;
import codeu.model.store.basic.NotificationTokenStore;
import codeu.model.store.basic.UserStore;
import codeu.model.store.basic.EmojiStore;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileNotFoundException;
import java.io.FileReader;

import java.io.File;
import java.lang.ClassLoader;

import javax.imageio.ImageIO;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.*;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import com.google.appengine.api.datastore.Blob;
import javax.servlet.http.Part;
import com.google.appengine.api.images.*;

/** Servlet class responsible for the chat page. */
@MultipartConfig
public class ChatServlet extends HttpServlet {

  /** Store class that gives access to Conversations. */
  private ConversationStore conversationStore;

  /** Store class that gives access to Messages. */
  private MessageStore messageStore;

  /** Store class that gives access to Users. */
  private UserStore userStore;

  /** Store class that gives access to Notification Tokens. */
  private NotificationTokenStore notificationTokenStore;

  private Map<String, String> validEmojis = new HashMap<>();

  private SendNotification sendNotification;

  private EmojiStore emojiStore;

  private Blob currentCustomEmoji;

  /** Set up state for handling chat requests. */
  @Override
  public void init() throws ServletException {
    super.init();
    setConversationStore(ConversationStore.getInstance());
    setMessageStore(MessageStore.getInstance());
    setUserStore(UserStore.getInstance());
    setNotificationTokenStore(NotificationTokenStore.getInstance());
    setSendNotification(new SendNotification());
    setEmojiStore(EmojiStore.getInstance());
    currentCustomEmoji = null;

    JSONParser parser = new JSONParser();
    try{
        File file = new File(getClass().getClassLoader().getResource("emoji/emojis.json").getFile());
        Object obj = parser.parse(new FileReader(file));
        JSONObject jsonObject = (JSONObject) obj;
        // loop array
        JSONArray emojis = (JSONArray) jsonObject.get("emojis");
        Iterator<JSONObject> iterator = emojis.iterator();
        while (iterator.hasNext()) {
            JSONObject emoji = iterator.next();
            String shortname = (String) emoji.get("shortname");
            String htmlCode = (String) emoji.get("html");
            if (shortname != null && !shortname.isEmpty() && shortname.length() > 2){
                validEmojis.put(shortname.substring(1, shortname.length()-1), htmlCode);
            }
        }
    } catch (FileNotFoundException e) {
        e.printStackTrace();
    } catch (IOException e) {
        e.printStackTrace();
    } catch (ParseException e) {
        e.printStackTrace();
    }
  }

  /**
   * Sets the ConversationStore used by this servlet. This function provides a common setup method
   * for use by the test framework or the servlet's init() function.
   */
  void setConversationStore(ConversationStore conversationStore) {
    this.conversationStore = conversationStore;
  }

  /**
   * Sets the MessageStore used by this servlet. This function provides a common setup method for
   * use by the test framework or the servlet's init() function.
   */
  void setMessageStore(MessageStore messageStore) {
    this.messageStore = messageStore;
  }

  /**
   * Sets the UserStore used by this servlet. This function provides a common setup method for use
   * by the test framework or the servlet's init() function.
   */
  void setUserStore(UserStore userStore) {
    this.userStore = userStore;
  }

  /**
   * Sets the NotificationTokenStore used by this servlet. This function provides a common setup method for use
   * by the test framework or the servlet's init() function.
   */
  void setNotificationTokenStore(NotificationTokenStore notificationTokenStore) {
    this.notificationTokenStore = notificationTokenStore;
  }

  void setSendNotification(SendNotification sendNotification){this.sendNotification = sendNotification;}


  void setEmojiStore(EmojiStore emojiStore){
    this.emojiStore = emojiStore;
  }

  /**
   * This function fires when a user navigates to the chat page. It gets the conversation title from
   * the URL, finds the corresponding Conversation, and fetches the messages in that Conversation.
   * It then forwards to chat.jsp for rendering.
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    String requestUrl = request.getRequestURI();
    String conversationTitle = requestUrl.substring("/chat/".length());
    currentCustomEmoji = null;

    Conversation conversation = conversationStore.getConversationWithTitle(conversationTitle);
    if (conversation == null) {
      // couldn't find conversation, redirect to conversation list
      System.out.println("Conversation was null: " + conversationTitle);
      response.sendRedirect("/conversations");
      return;
    }

    UUID conversationId = conversation.getId();

    List<Message> messages = messageStore.getMessagesInConversation(conversationId);

    request.setAttribute("conversation", conversation);
    request.setAttribute("messages", messages);
    request.getRequestDispatcher("/WEB-INF/view/chat.jsp").forward(request, response);
  }


  /**
  Converts message string into ArrayList of meaningful tokens.
  */
  public ArrayList<String> tokenizeMessage(String cleanedMessageContent,
                                           List<Character> validCharFlags,
                                           List<String> validStrFlags,
                                           List<String> linkPrefix){
      ArrayList<String> tokenizedMessageContent = new ArrayList();
      int cleanedMessageLength = cleanedMessageContent.length();
      for (int i = 0; i < cleanedMessageLength; i++) {
          if (validCharFlags.contains(cleanedMessageContent.charAt(i))){
              if (i+1 < cleanedMessageLength && validStrFlags.contains("" +
                      cleanedMessageContent.charAt(i) + cleanedMessageContent.charAt(i+1))){
                  tokenizedMessageContent.add("" +
                          cleanedMessageContent.charAt(i) + cleanedMessageContent.charAt(i));
                  i++;
              }
              else{
                  tokenizedMessageContent.add(""+cleanedMessageContent.charAt(i));
              }
          }
          else{
              boolean inLink = false;
              for (String prefix: linkPrefix){
                  if(i + prefix.length() < cleanedMessageLength &&
                        prefix.equals(cleanedMessageContent.substring(i, i+prefix.length()))) {
                      tokenizedMessageContent.add(prefix);
                      i += prefix.length()-1;
                      inLink = true;
                  }
              }
              if(!inLink){
                  tokenizedMessageContent.add(""+cleanedMessageContent.charAt(i));
              }
          }
      }
      return tokenizedMessageContent;
  }

  /**
  Parses tokens and replaces supported flags with the correct HTML syntax.
  */
  public ArrayList<Blob> parseMessage(ArrayList<String> tokenizedMessageContent,
                           List<Character> validCharFlags,
                           List<String> validStrFlags,
                           List<String> linkPrefix,
                           String emojiFlag,
                           Map<String, String[]> markToHtml){

      Hashtable<String, Blob> customEmojis = new Hashtable<String, Blob>();
      ArrayList<Blob> customEmojisToReturn = new ArrayList<Blob>();
      if (emojiStore != null){
        customEmojis = emojiStore.getEmojiTable();
      }

      for (int i = 0; i < tokenizedMessageContent.size(); i++){
          if (validStrFlags.contains(tokenizedMessageContent.get(i))){
              for (int j = tokenizedMessageContent.size() - 1; j > i; j--){
                  if(tokenizedMessageContent.get(i).equals(tokenizedMessageContent.get(j))){
                      String mark = tokenizedMessageContent.get(i);
                      tokenizedMessageContent.set(i, markToHtml.get(mark)[0]);
                      tokenizedMessageContent.set(j, markToHtml.get(mark)[1]);
                      break;
                  }
              }
          }
          if (emojiFlag.equals(tokenizedMessageContent.get(i))){
              String shortcode = "";
              Boolean validSyntax = false;
              int j;
              // finds the next emojiFlag, marking the end of the shortcode
              for (j = i+1; j < tokenizedMessageContent.size(); j++){
                  if(emojiFlag.equals(tokenizedMessageContent.get(j))){
                      validSyntax = true;
                      break;
                  }
                  else{
                      shortcode += tokenizedMessageContent.get(j);
                  }
              }
              // if there was a closing emojiFlag and the shortcode is valid
              // then replace the shortcode and flags with emoji html code

              // adds custom emoji to arraylist and '|' char to mark location
              // of the custom emoji
              if(validSyntax && customEmojis.containsKey(shortcode)){
                  customEmojisToReturn.add(customEmojis.get(shortcode));
                  tokenizedMessageContent.set(i, "|");
                  for (int k = j; k > i; k--){
                      tokenizedMessageContent.remove(k);
                  }
              }

              // inserts the code corresponding to the native emoji shortcode
              else if (validSyntax && validEmojis.containsKey(shortcode)){
                  tokenizedMessageContent.set(i, validEmojis.get(shortcode));
                  for (int k = j; k > i; k--){
                      tokenizedMessageContent.remove(k);
                  }
              }

          }
          if (linkPrefix.contains(tokenizedMessageContent.get(i))){
              // if a link prefix is found, find the next ' ' space character
              // marking the end of the link, and insert the proper html syntax
              tokenizedMessageContent.add(i, markToHtml.get("LINK")[0]);
              for (int j = i+1; j < tokenizedMessageContent.size(); j++){
                  if (tokenizedMessageContent.get(j).equals(" ") ||
                          j == tokenizedMessageContent.size()-1){
                      if (tokenizedMessageContent.get(j).equals(" ")){
                          j--;
                      }
                      String linkContents = "";
                      for (int k = i+1; k < j+1; k++){
                          linkContents += tokenizedMessageContent.get(k);
                      }
                      tokenizedMessageContent.add(j+1, markToHtml.get("LINK")[1] +
                              linkContents + markToHtml.get("LINK")[2]);
                      i++;
                      break;
                  }
              }
          }
      }
      return customEmojisToReturn;
  }

  /**
   * This function fires when a user submits the form on the chat page. It gets the logged-in
   * username from the session, the conversation title from the URL, and the chat message from the
   * submitted form data. It creates a new Message from that data, adds it to the model, and then
   * redirects back to the chat page.
   */
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {

    String username = (String) request.getSession().getAttribute("user");
    if (username == null) {
      // user is not logged in, don't let them add a message
      response.sendRedirect("/login");
      return;
    }

    User user = userStore.getUser(username);
    if (user == null) {
      // user was not found, don't let them add a message
      response.sendRedirect("/login");
      return;
    }

    String requestUrl = request.getRequestURI();
    String conversationTitle = requestUrl.substring("/chat/".length());

    Conversation conversation = conversationStore.getConversationWithTitle(conversationTitle);
    if (conversation == null) {
      // couldn't find conversation, redirect to conversation list
      response.sendRedirect("/conversations");
      return;
    }

    String messageContent = request.getParameter("message");


    // this removes any HTML from the message content
    String cleanedMessageContent = Jsoup.clean(messageContent, Whitelist.none());

    int cleanedMessageLength = cleanedMessageContent.length();

    //character and string representations of supported markdown flags
    List<Character> validCharFlags = Arrays.asList('*', '_', '`');
    List<String> validStrFlags = Arrays.asList("*", "_", "`", "**", "__");
    List<String> linkPrefix = Arrays.asList("http://", "https://", "www.");

    //use this to surround emoji shortcodes (:thumbsup:)
    String emojiFlag = ":";

    Map<String, String[]> markToHtml = new HashMap<>();

    markToHtml.put("*", new String[]{"<em>", "</em>"});
    markToHtml.put("_", new String[]{"<em>", "</em>"});
    markToHtml.put("`", new String[]{"<code>", "</code>"});
    markToHtml.put("**", new String[]{"<strong>", "</strong>"});
    markToHtml.put("__", new String[]{"<strong>", "</strong>"});
    markToHtml.put("LINK", new String[]{"<a href=\"", "\" target=\"_blank\">","</a>"});

    // tokenizes message into array list of strings
    ArrayList<String> tokenizedMessageContent = tokenizeMessage(cleanedMessageContent,
            validCharFlags, validStrFlags, linkPrefix);

    // matches valid pairs of tokens and replaces with html syntax
    ArrayList<Blob> requestedCustomEmojis = parseMessage(tokenizedMessageContent,
              validCharFlags, validStrFlags,linkPrefix, emojiFlag, markToHtml);

    // converts ArrayList to string
    String parsedMessageContent = "";
    for (String token:tokenizedMessageContent){
      parsedMessageContent += token;
    }

    Message message;

    Part filePart = request.getPart("image"); // Retrieves <input type="file" name="image">
    String checkboxOut = request.getParameter("emoji-checkbox");
    String shortcode = request.getParameter("shortcode");
    boolean messageToSend = true;

    if (checkboxOut == null){
      checkboxOut = "off";
    }

    if(checkboxOut.equals("on") && filePart != null && !filePart.getSubmittedFileName().equals("") && shortcode != null && !shortcode.equals("")){  //user wants their uploaded image to be an emoji
      // Handles when the user uploads a new custom emoji
      InputStream fileContent = filePart.getInputStream();

      ImagesService imagesService = ImagesServiceFactory.getImagesService();
      Image originalImage = ImagesServiceFactory.makeImage(IOUtils.toByteArray(fileContent));
      Transform resize = ImagesServiceFactory.makeResize(20, 20);
      Image resizedImage = imagesService.applyTransform(resize, originalImage);

      Blob image = new Blob(resizedImage.getImageData());
      emojiStore.addEmoji(shortcode, image);
      messageToSend = false;
      message = //not needed, but gets rid of compiler error "might not be initialized"
          new Message(
              UUID.randomUUID(),
              conversation.getId(),
              user.getId(),
              parsedMessageContent,
              Instant.now());
    }
    else if(filePart != null && !filePart.getSubmittedFileName().equals("") && requestedCustomEmojis.size() > 0){
      // Handles when the user uploads an image and includes custom emojis in their message
      InputStream fileContent = filePart.getInputStream();

      ImagesService imagesService = ImagesServiceFactory.getImagesService();
      Image originalImage = ImagesServiceFactory.makeImage(IOUtils.toByteArray(fileContent));
      Transform resize = ImagesServiceFactory.makeResize(350, 600);
      Image resizedImage = imagesService.applyTransform(resize, originalImage);

      Blob image = new Blob(resizedImage.getImageData());

      message =
          new Message(
              UUID.randomUUID(),
              conversation.getId(),
              user.getId(),
              parsedMessageContent,
              Instant.now(),
              image,
              requestedCustomEmojis);
    }
    else if(filePart != null && !filePart.getSubmittedFileName().equals("")){
      // Handles when the user uploads an image
      InputStream fileContent = filePart.getInputStream();

      ImagesService imagesService = ImagesServiceFactory.getImagesService();
      Image originalImage = ImagesServiceFactory.makeImage(IOUtils.toByteArray(fileContent));
      Transform resize = ImagesServiceFactory.makeResize(350, 600);
      Image resizedImage = imagesService.applyTransform(resize, originalImage);

      Blob image = new Blob(resizedImage.getImageData());

      message =
          new Message(
              UUID.randomUUID(),
              conversation.getId(),
              user.getId(),
              parsedMessageContent,
              Instant.now(),
              image);
    }
    else if(requestedCustomEmojis.size() > 0){
      // Handles when the user includes custom emojis in their message
      message =
          new Message(
              UUID.randomUUID(),
              conversation.getId(),
              user.getId(),
              parsedMessageContent,
              Instant.now(),
              requestedCustomEmojis);
    }

    else{
      // Handles plain text messages
      message =
          new Message(
              UUID.randomUUID(),
              conversation.getId(),
              user.getId(),
              parsedMessageContent,
              Instant.now());
    }

    if(messageToSend){
      //send notification
      List <String> tokens = conversation.getSubscribersTokens();
      for(String token:tokens) {
        sendNotification.sendMsg(parsedMessageContent, token, notificationTokenStore.getMessagingAPIKey());
      }

      messageStore.addMessage(message);
    }
    // redirect to a GET request
    response.sendRedirect("/chat/" + conversationTitle);
  }
}
