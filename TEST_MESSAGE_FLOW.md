# Test Message Loading Flow

## Current Logic Summary

### 1. Database Layer (MessageDAO)
```
SQL: ORDER BY created_at DESC
Result: [msg5(newest), msg4, msg3, msg2, msg1(oldest)]
Reverse: [msg1(oldest), msg2, msg3, msg4, msg5(newest)]
Return: Chronological order (old → new)
```

### 2. ConversationLoader - Initial Load
```
1. loadConversation() called
   - Clear currentMessages
   - Reset currentOffset = 0
   - Clear UI
   
2. loadMoreMessages(versionSnapshot)
   - Fetch 20 messages from offset 0
   - DB returns: [msg1, msg2, ..., msg20] (old → new)
   
3. Process fetched messages
   - currentMessages.addAll(fetched)
   - currentMessages = [msg1, msg2, ..., msg20]
   
4. rebuildMessageList()
   - Add loadMoreButton (if hasMore)
   - For each message in currentMessages:
       messagesContainer.add(messageNode)
   - UI: [LoadMore] [msg1] [msg2] ... [msg20]
   
5. scrollToBottomDelayed()
   - Scroll to show msg20 (newest)
```

### 3. Expected Result
```
UI Display (top to bottom):
┌─────────────────┐
│ [Tải thêm]      │  ← Load more button (if hasMore)
│ msg1 (oldest)   │
│ msg2            │
│ msg3            │
│ ...             │
│ msg20 (newest)  │  ← Should be visible after scroll
└─────────────────┘
```

## Test Cases

### Test 1: Initial Load Private Chat
**Steps:**
1. Click on user A
2. Check messages display

**Expected:**
- Messages in chronological order (old → new)
- Scroll at bottom showing newest message
- Load more button at top (if > 20 messages)

### Test 2: Initial Load Group Chat
**Steps:**
1. Click on group G
2. Check messages display

**Expected:**
- Same as Test 1

### Test 3: New Message Arrives
**Steps:**
1. Open conversation with user A
2. User A sends new message
3. Check display

**Expected:**
- New message appears at bottom
- Auto scroll to show new message

### Test 4: Load More Messages
**Steps:**
1. Open conversation with > 20 messages
2. Click "Tải thêm" button
3. Check display

**Expected:**
- Older messages added at top
- Current scroll position maintained
- Can see the message you were viewing before

### Test 5: Switch Between Conversations
**Steps:**
1. Open conversation with user A
2. Open conversation with user B
3. Back to user A

**Expected:**
- Each conversation loads correctly
- No mixing of messages
- Correct scroll position

## Debug Commands

### Check message order in database:
```sql
-- Private messages
SELECT message_id, sender_id, receiver_id, content, created_at 
FROM messages 
WHERE (sender_id = 1 AND receiver_id = 2) OR (sender_id = 2 AND receiver_id = 1)
ORDER BY created_at DESC 
LIMIT 20;

-- Group messages
SELECT message_id, sender_id, group_id, content, created_at 
FROM messages 
WHERE group_id = 1
ORDER BY created_at DESC 
LIMIT 20;
```

### Check logs:
Look for these log messages:
- "Loading conversation for contact=..."
- "Fetching messages: offset=..., limit=..."
- "Fetched X messages"
- "Initial fetch: X messages loaded"
- "Rebuilt message list: X messages rendered"
- "Scrolled to bottom, vvalue=..."

## Common Issues and Fixes

### Issue 1: Messages in wrong order
**Symptom:** Newest message at top instead of bottom
**Check:** MessageDAO reverse logic
**Fix:** Ensure reverse is working correctly

### Issue 2: Duplicate messages
**Symptom:** Same message appears multiple times
**Check:** handleIncomingMessage() logic
**Fix:** Check messageExists() before adding

### Issue 3: Scroll not at bottom
**Symptom:** Opens conversation but shows old messages
**Check:** scrollToBottomDelayed() timing
**Fix:** Increase delay or add more scroll attempts

### Issue 4: Load more loads wrong messages
**Symptom:** Clicking "Tải thêm" shows wrong messages
**Check:** currentOffset calculation
**Fix:** Ensure offset increments correctly

### Issue 5: Switching conversations shows wrong messages
**Symptom:** User A's messages show in User B's chat
**Check:** conversationVersion and isCurrentVersion()
**Fix:** Ensure version increments on each loadConversation()



