package com.intetics.organizerbot;

import com.intetics.organizerbot.bean.LessonBean;
import com.intetics.organizerbot.context.ContextHolder;
import com.intetics.organizerbot.context.Context;
import com.intetics.organizerbot.context.TypeOfClass;
import com.intetics.organizerbot.entities.Lesson;
import com.intetics.organizerbot.entities.LessonType;
import com.intetics.organizerbot.keyboards.Keyboards;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.CallbackQuery;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.logging.BotLogger;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class OrganizerBot extends TelegramLongPollingBot {

    private static final String LOGTAG = "BOT";

    private ResourceBundle botInfo = ResourceBundle.getBundle("botinfo");
    private ResourceBundle buttons = ResourceBundle.getBundle("buttons");
    private ResourceBundle messages = ResourceBundle.getBundle("messages");
    private static ResourceBundle days = ResourceBundle.getBundle("days");

    DAO dao = new DAO();

    public String getBotUsername() {
        return botInfo.getString("username");
    }

    @Override
    public String getBotToken() {
        return botInfo.getString("token");
    }

    private class UpdateHandler extends Thread {
        private Update update;

        public UpdateHandler(Update update) {
            super();
            this.update = update;
        }

        @Override
        public void run() {
            handleUpdate(update);
        }
    }

    public void handleUpdate(Update update){
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    public void onUpdateReceived(Update update) {
        UpdateHandler updateHandler = new UpdateHandler(update);
        updateHandler.run();
    }

    private void handleCallbackQuery(CallbackQuery query) {
        Context context = ContextHolder.getInstance().getContext(query.getMessage().getChatId());
        switch (context) {
            case ADD_CLASS_CHOOSE_DATE:
                handleCallbackQueryFromAddClassChooseDate(query);
                break;
            case SHOW_TIMETABLE:
                handleCallbackQueryFormShowTimetable(query);
                break;
            case SHOW_TIMETABLE_RANGE:
                handleCallbackQueryFormShowTimetableRange(query);
        }
    }

    private void handleCallbackQueryFormShowTimetableRange(CallbackQuery query) {
        String data = query.getData();
        if (data.startsWith("goto:")) {
            resetCalendar(query);
        } else if (data.startsWith("choose:")) {
            LocalDate date = LocalDate.parse(data.split(":")[1]);
            handleChoosingRange(query, date);
        }
    }

    private void handleChoosingRange(CallbackQuery query, LocalDate date) {
        if (ContextHolder.getInstance().isEditing(query.getMessage().getChatId())){
            LocalDate firstDate = (LocalDate) getEditingValue(query.getMessage().getChatId());
            if (firstDate.isAfter(date)) {
                LocalDate tmp = firstDate;
                firstDate = date;
                date = tmp;
            }
            do {
                showTimetable(query.getMessage(), firstDate);
                firstDate = firstDate.plusDays(1);
            } while (!firstDate.isAfter(date));
            setContext(query.getMessage().getChatId(), Context.MAIN_MENU);
            sendMainMenu(query.getMessage());
            ContextHolder.getInstance().removeEditingValue(query.getMessage().getChatId());
        } else {
            setEditingValue(query.getMessage().getChatId(), date);
            reply(query.getMessage(), date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)));
            reply(query.getMessage(), messages.getString("chooseSecondDate"));
        }
    }

    private void handleCallbackQueryFormShowTimetable(CallbackQuery query) {
        String data = query.getData();
        if (data.startsWith("goto:")) {
            resetCalendar(query);
        } else if (data.startsWith("choose:")) {
            LocalDate date = LocalDate.parse(data.split(":")[1]);
            showTimetable(query.getMessage(), date);
            setContext(query.getMessage().getChatId(), Context.MAIN_MENU);
            sendMainMenu(query.getMessage());
        }
    }

    private void handleCallbackQueryFromAddClassChooseDate(CallbackQuery query) {
        String data = query.getData();
        if (data.startsWith("goto:")) {
            resetCalendar(query);
        } else if (data.startsWith("choose:")) {
            LocalDate date = LocalDate.parse(data.split(":")[1]);
            setDate(query, date);
            reply(query.getMessage(), date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)));
            setContext(query.getMessage().getChatId(), Context.ADD_CLASS_CHOOSE_TIME);
            reply(query.getMessage(), messages.getString("chooseTime"), Keyboards.getReturnToMenuKeyboard());
        }
    }

    private void setDate(CallbackQuery query, LocalDate date) {
        LessonBean lesson = (LessonBean) getEditingValue(query.getMessage().getChatId());
        lesson.setDate(date);
    }

    private void resetCalendar(CallbackQuery query) {
        EditMessageText editMarkup = new EditMessageText();
        editMarkup.setChatId(query.getMessage().getChatId().toString());
        editMarkup.setInlineMessageId(query.getInlineMessageId());
        editMarkup.enableMarkdown(true);
        editMarkup.setText(messages.getString("chooseDate2"));
        editMarkup.setMessageId(query.getMessage().getMessageId());
        editMarkup.setReplyMarkup(Keyboards.getCalendarKeyboard(YearMonth.parse(query.getData().split(":")[1])));
        try {
            execute(editMarkup);
        } catch (TelegramApiException e) {
            BotLogger.severe(LOGTAG, e);
        }
    }

    private void handleMessage(Message message) {
        Long userId = message.getChatId();
        if (!ContextHolder.getInstance().contains(message.getChatId())) {
            setContext(userId, Context.MAIN_MENU);
        }
        Context chatContext = ContextHolder.getInstance().getContext(userId);
        handleMessageInContext(message, chatContext);
    }

    private void handleMessageInContext(Message message, Context context) {
        switch (context) {
            case MAIN_MENU:
                handleMessageFromMainMenu(message);
                break;
            case ADD_CLASS_CHOOSE_SUBJECT:
                handleMessageFromAddClassChooseSubject(message);
                break;
            case ADD_CLASS_CHOOSE_DATE_OR_DAY:
                handleMessageFromAddClassChooseDateOrDay(message);
                break;
            case ADD_CLASS_CHOOSE_DAY:
                handleMessageFromAddClassChooseDay(message);
                break;
            case ADD_CLASS_CHOOSE_DATE:
                handleMessageFromAddClassChooseDate(message);
                break;
            case ADD_CLASS_CHOOSE_TIME:
                handleMessageFromAddClassChooseTime(message);
                break;
            case ADD_CLASS_CHOOSE_TYPE:
                handleMessageFromAddClassChooseType(message);
                break;
            case ADD_CLASS_CHOOSE_PROFESSOR:
                handleMessageFromAddClassChooseProfessor(message);
                break;
            case ADD_CLASS_CHOOSE_ROOM:
                handleMessageFromAddClassChooseRoom(message);
                break;
            case SHOW_TIMETABLE:
                handleMessageFromShowTimetable(message);
                break;
            case SHOW_TIMETABLE_RANGE:
                handleMessageFromShowTimetableRange(message);
                break;
        }
    }

    private void handleMessageFromShowTimetableRange(Message message) {
        String text = message.getText();
        if (buttons.getString("mainMenu").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            sendMainMenu(message);
        } else {
            String replyText = messages.getString("cannotUnderstand") + messages.getString("chooseDate1");
            reply(message, replyText);
        }
    }

    private void handleMessageFromShowTimetable(Message message) {
        String text = message.getText();
        if (buttons.getString("mainMenu").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            sendMainMenu(message);
        } else if (buttons.getString("forToday").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            showTimetable(message, LocalDate.now());
            sendMainMenu(message);
        } else if (buttons.getString("forTomorrow").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            showTimetable(message, LocalDate.now().plusDays(1));
            sendMainMenu(message);
        } else if (buttons.getString("forOtherDate").equals(text)) {
            reply(message, messages.getString("chooseDate1"), Keyboards.getReturnToMenuKeyboard());
            reply(message, messages.getString("chooseDate2"), Keyboards.getCalendarKeyboard());
        } else if (buttons.getString("forWeek").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            LocalDate today = LocalDate.now();
            for (int i = 0; i < 7; i++) {
                showTimetable(message, today.plusDays(i));
            }
            sendMainMenu(message);
        } else if (buttons.getString("forRangeOfDates").equals(text)) {
            setContext(message.getChatId(), Context.SHOW_TIMETABLE_RANGE);
            reply(message, messages.getString("chooseFirstDate"), Keyboards.getReturnToMenuKeyboard());
            reply(message, messages.getString("chooseDate2"), Keyboards.getCalendarKeyboard());
        } else {
            String replyText = messages.getString("cannotUnderstand") + ' ' + messages.getString("chooseFromMenu");
            reply(message, replyText);
        }
    }

    private void showTimetable(Message message, LocalDate date) {
        List<Lesson> lessons = dao.getLessonByDate(date, Math.toIntExact(message.getChatId()));
        String text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) + "\r\n";
        for (Lesson lesson : lessons){
            text += "------------------------\r\n" +
                    lesson.getTime().toString() + " - " +
                    lesson.getSubjects().getSubjectTitle() + " (" +
                    LessonType.values()[lesson.getType()].toString().toLowerCase() + ")\r\n" +
                    lesson.getRoom() + "\r\n" +
                    lesson.getProfessor().getProfessorName() + "\r\n";
        }
        reply(message, text);
    }

    private void handleMessageFromAddClassChooseProfessor(Message message) {
        String text = message.getText();
        if (buttons.getString("mainMenu").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            sendMainMenu(message);
        } else {
            setProfessor(message, text);
            setContext(message.getChatId(), Context.ADD_CLASS_CHOOSE_ROOM);
            reply(message, messages.getString("choosePlace"), Keyboards.getReturnToMenuKeyboard());
        }
    }

    private void setProfessor(Message message, String text) {
        LessonBean lesson = (LessonBean) getEditingValue(message.getChatId());
        dao.createProfessor(text);
        lesson.setProfessorName(text);
    }

    private void handleMessageFromAddClassChooseDate(Message message) {
        String text = message.getText();
        System.out.println("message:" + text + "end");
        if (buttons.getString("mainMenu").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            sendMainMenu(message);
        } else {
            String replyText = messages.getString("cannotUnderstand") + ' ' + messages.getString("chooseDate1");
            reply(message, replyText);
        }
    }

    private void handleMessageFromAddClassChooseRoom(Message message) {
        String text = message.getText();
        if (!buttons.getString("mainMenu").equals(text)) {
            LessonBean lesson = (LessonBean) getEditingValue(message.getChatId());
            setLesson(message, text);
            reply(message, messages.getString("classAdded"));
            showTimetable(message, lesson.getDate());
        }
        setContext(message.getChatId(), Context.MAIN_MENU);
        sendMainMenu(message);
    }

    private void setLesson(Message message, String room) {
        LessonBean lesson = (LessonBean) getEditingValue(message.getChatId());
        lesson.setRoom(room);
        dao.createLesson(
                Math.toIntExact(message.getChatId()),
                lesson.getSubject(),
                lesson.getDate(),
                lesson.getTime(),
                lesson.getRoom(),
                lesson.getType(),
                lesson.getProfessorName());
        if (ContextHolder.getInstance().getTypeOfClass(message.getChatId()).equals(TypeOfClass.WEEKLY)){
            while (lesson.getDate().getYear() == 2017){
                lesson.setDate(lesson.getDate().plusDays(7));
                dao.createLesson(
                        Math.toIntExact(message.getChatId()),
                        lesson.getSubject(),
                        lesson.getDate(),
                        lesson.getTime(),
                        lesson.getRoom(),
                        lesson.getType(),
                        lesson.getProfessorName());
            }
        }
        ContextHolder.getInstance().removeEditingValue(message.getChatId());
    }

    private void handleMessageFromAddClassChooseType(Message message) {
        String text = message.getText();
        if (buttons.getString("mainMenu").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            sendMainMenu(message);
        } else {
            LessonType lessonType = LessonType.fromString(text);
            if (lessonType == null) {
                String replyText = messages.getString("cannotUnderstand") + ' ' + messages.getString("chooseFromMenu");
                reply(message, replyText);
            }
            setClassType(message, lessonType);
            setContext(message.getChatId(), Context.ADD_CLASS_CHOOSE_PROFESSOR);
            reply(message, messages.getString("chooseProfessor"), Keyboards.getListKeyboard(getProfessors()));
        }
    }

    private void setClassType(Message message, LessonType lessonType) {
        LessonBean lesson = (LessonBean) getEditingValue(message.getChatId());
        lesson.setType(lessonType.ordinal());
    }

    private void handleMessageFromAddClassChooseDateOrDay(Message message) {
        String text = message.getText();
        if (buttons.getString("mainMenu").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            sendMainMenu(message);
        } else if (buttons.getString("oneTime").equals(text)) {
            setContext(message.getChatId(), Context.ADD_CLASS_CHOOSE_DATE);
            ContextHolder.getInstance().setTypeOfClass(message.getChatId(), TypeOfClass.ONE_TIME);
            reply(message, messages.getString("chooseDate1"), Keyboards.getReturnToMenuKeyboard());
            reply(message, messages.getString("chooseDate2"), Keyboards.getCalendarKeyboard());
        } else if (buttons.getString("weekly").equals(text)) {
            setContext(message.getChatId(), Context.ADD_CLASS_CHOOSE_DAY);
            ContextHolder.getInstance().setTypeOfClass(message.getChatId(), TypeOfClass.WEEKLY);
            reply(message, messages.getString("chooseDay"), Keyboards.getDaysListKeyboard());
        } else {
            String replyText = messages.getString("cannotUnderstand") + ' ' + messages.getString("chooseFromMenu");
            reply(message, replyText);
        }
    }

    private void handleMessageFromAddClassChooseSubject(Message message) {
        String text = message.getText();
        if (buttons.getString("mainMenu").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            sendMainMenu(message);
        } else {
            setSubject(message, text);
            setContext(message.getChatId(), Context.ADD_CLASS_CHOOSE_DATE_OR_DAY);
            reply(message, messages.getString("chooseDateOrDay"), Keyboards.getOneTimeOrWeeklyKeyboard());
        }
    }

    private void setSubject(Message message, String text) {
        LessonBean lesson = new LessonBean();
        dao.createSubjects(Math.toIntExact(message.getChatId()), text);
        lesson.setSubject(text);
        setEditingValue(message.getChatId(), lesson);
    }

    private void handleMessageFromAddClassChooseTime(Message message) {
        String text = message.getText();
        if (buttons.getString("mainMenu").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            sendMainMenu(message);
        } else if (validTime(text)) {
            if (text.length() == 4) {
                text = "0" + text;
            }
            setTime(message, text);
            setContext(message.getChatId(), Context.ADD_CLASS_CHOOSE_TYPE);
            reply(message, messages.getString("chooseType"), Keyboards.getClassTypesListKeyboard());
        } else {
            String replyText = messages.getString("cannotUnderstand") + ' ' + messages.getString("chooseTime");
            reply(message, replyText);
        }
    }

    private void setTime(Message message, String text) {
        LessonBean lesson = (LessonBean) getEditingValue(message.getChatId());
        lesson.setTime(LocalTime.parse(text));
    }

    private boolean validTime(String string) {
        return string.matches("^([0-9]|0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$");
    }

    private List<String> getSubjects(Long id) {
        List<String> subjectNames = new ArrayList<>();
        dao.getSubjectsByUserId(Math.toIntExact(id)).forEach(subject -> subjectNames.add(subject.getSubjectTitle()));
        return subjectNames;
    }

    private void handleMessageFromAddClassChooseDay(Message message) {
        String text = message.getText();
        if (validDayOfWeek(text)) {
            setDay(message, text);
            reply(message, messages.getString("chooseTime"), Keyboards.getReturnToMenuKeyboard());
            setContext(message.getChatId(), Context.ADD_CLASS_CHOOSE_TIME);
        } else if (buttons.getString("mainMenu").equals(text)) {
            setContext(message.getChatId(), Context.MAIN_MENU);
            sendMainMenu(message);
        } else {
            String replyText = messages.getString("cannotUnderstand") + ' ' + messages.getString("chooseDay");
            reply(message, replyText);
        }
    }

    private void setDay(Message message, String text) {
        LessonBean lesson = (LessonBean) getEditingValue(message.getChatId());
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(text.toUpperCase());//TODO: rewrite line
        LocalDate date = LocalDate.now();
        if (date.getDayOfWeek().getValue() > dayOfWeek.getValue()) {
            date = date.plusDays(7 - (date.getDayOfWeek().getValue() - dayOfWeek.getValue()));
        } else {
            date = date.plusDays(dayOfWeek.getValue() - date.getDayOfWeek().getValue());
        }
        lesson.setDate(date);
    }

    private void handleMessageFromMainMenu(Message message) {
        if (buttons.getString("addClass").equals(message.getText())) {
            List<String> subjects = getSubjects(message.getChatId());
            reply(message, messages.getString("chooseSubject"), Keyboards.getListKeyboard(subjects));
            setContext(message.getChatId(), Context.ADD_CLASS_CHOOSE_SUBJECT);
        } else if (buttons.getString("showTimetable").equals(message.getText())) {
            reply(message, messages.getString("chooseView"), Keyboards.getViewsKeyboard());
            setContext(message.getChatId(), Context.SHOW_TIMETABLE);
        } else if ("/start".equals(message.getText())) {
            dao.createUser(Math.toIntExact(message.getChatId()), message.getChat().getFirstName(), "no");
            sendMainMenu(message);
        } else {
            sendMainMenu(message, messages.getString("cannotUnderstand") + messages.getString("chooseFromMenu"));
        }
    }

    private boolean validDayOfWeek(String string) {
        for (String dayKey : days.keySet()) {
            if (days.getString(dayKey).equals(string)) {
                return true;
            }
        }
        return false;
    }

    private void setContext(Long id, Context addSubject) {
        ContextHolder.getInstance().setContext(id, addSubject);
    }

    private void setEditingValue(Long id, Object value) {
        ContextHolder.getInstance().setEditingValue(id, value);
    }

    private Object getEditingValue(Long id) {
        return ContextHolder.getInstance().getEditingValue(id);
    }

    private void reply(Message inMessage, String text) {
        SendMessage outMessage = new SendMessage();
        outMessage.setChatId(inMessage.getChatId());
        outMessage.setText(text);
        send(outMessage);
    }

    private void reply(Message inMessage, String text, ReplyKeyboard keyboard) {
        SendMessage outMessage = new SendMessage();
        outMessage.setChatId(inMessage.getChatId());
        outMessage.setText(text);
        outMessage.setReplyMarkup(keyboard);
        send(outMessage);
    }

    private void sendMainMenu(Message inMessage) {
        sendMainMenu(inMessage, messages.getString("chooseOption"));
    }

    private void sendMainMenu(Message inMessage, String outMessageText) {
        reply(inMessage, outMessageText, Keyboards.getMainMenuKeyboard());
    }

    private void send(SendMessage outMessage) {
        try {
            execute(outMessage);
        } catch (TelegramApiException e) {
            BotLogger.severe(LOGTAG, e);
        }
    }

    public List<String> getProfessors() {
        return new ArrayList<String>() {{
        }};
    }
}
