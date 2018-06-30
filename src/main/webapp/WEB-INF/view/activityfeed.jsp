<%--
  Copyright 2017 Google Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
--%>
<%@ page import="java.util.*"%>
<%@ page import="java.util.Arrays"%>
<%@ page import="java.time.*"%>
<%@ page import="java.time.format.DateTimeFormatter"%>
<%@ page import="codeu.model.data.Conversation"%>
<%@ page import="codeu.model.data.User"%>
<%@ page import="codeu.model.data.Message"%>
<%@ page import="codeu.model.data.Event"%>
<%@ page import="codeu.model.store.basic.UserStore"%>
<%@ page import="java.time.format.FormatStyle"%>
<%@ page import="codeu.model.store.basic.ConversationStore"%>
<%@ page import="codeu.model.store.basic.MessageStore"%>
<!DOCTYPE html>
<html>
<head>
<title>Activity Feed</title>

<link rel="stylesheet" href="/css/main.css">

  <style>
    #feed {
      background-color: white;
      height: 500px;
      overflow-y: scroll
    }
  </style>

  <script>
    // scroll the chat div to the bottom
    function scrollChat() {
      var eventDiv = document.getElementById('feed');
      eventDiv.scrollTop = eventDiv.scrollHeight;
    };
  </script>
  
</head>

<body onload="scrollChat()">

	<nav>
		<a id="navTitle" href="/">CodeU Chat App</a> <a href="/conversations">Conversations</a>
		<a href="/about.jsp">About</a>
	</nav>

	<div id="container">
		<h1>Activity</h1>

		<p>See what's happening!</p>
		<div id="feed">
			<ul>
				<%
				  DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
				      .withLocale(Locale.US).withZone(ZoneId.systemDefault());
				  HashMap<Instant, Event> eventsMap =
				      (HashMap<Instant, Event>) request.getAttribute("eventsMap");
				  ArrayList<Instant> eventsInstantsSorted =
				      (ArrayList<Instant>) request.getAttribute("eventsInstantsSorted");
				  if (eventsMap == null) {
				    return;
				  } else {
				    for (Instant instant : eventsInstantsSorted) {
				      String author;
				      Instant time;
				      String title;
				      for (Map.Entry<Instant, Event> m : eventsMap.entrySet()) {
				        if (m.getKey() == instant) {
				          Event event = m.getValue();
				          if (event.getEventType() == "user") {
				            author = UserStore.getInstance().getUser(event.getId()).getName();
				%>
				<li><b><%=formatter.format(m.getKey())%></b>: <%=author%>
					joined!</li>
				<%
				  } else if (event.getEventType() == "conversation") {
				            Conversation conversation =
				                ConversationStore.getInstance().getConversation(event.getId());
				            author = UserStore.getInstance().getUser(conversation.getOwnerId()).getName();
				            title = conversation.getTitle();
				%>
				<li><b><%=formatter.format(m.getKey())%></b>: <%=author%>
					created a new conversation: <a href="/chat/<%=title%>"><%=title%></a></li>
				<%
				  } else if (event.getEventType() == "message") {
				            Message message = MessageStore.getInstance().getMessage(event.getId());
				            author = UserStore.getInstance().getUser(message.getAuthorId()).getName();
				            String conversationTitle = ConversationStore.getInstance()
				                .getConversation(message.getConversationId()).getTitle();
				%>
				<li><b><%=formatter.format(m.getKey())%></b>: <%=author%> sent
					a message in <a href="/chat/<%=conversationTitle%>"><%=conversationTitle%></a>:
					"<%=message.getContent()%>"</li>
				<%
				  }
				%>

				<%
				  }
				      }
				    }
				  }
				%>
			
		</div>
		</ul>
	</div>
</body>
</html>
