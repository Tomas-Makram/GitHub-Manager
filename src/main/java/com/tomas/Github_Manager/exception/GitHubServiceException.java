package com.tomas.Github_Manager.exception;

/**
 * خطأ منطقي من الـ Service (مش من GitHub مباشرة).
 * بيشيل كود الحالة ومفتاح الرسالة المترجمة والمعاملات، والـ Controller بيحوّل المفتاح لرسالة.
 */
public class GitHubServiceException extends RuntimeException {

    private final int status;
    private final String messageKey;
    private final Object[] args;

    /** بينشئ الاستثناء بكود الحالة ومفتاح الرسالة وأي معاملات للرسالة. */
    public GitHubServiceException(int status, String messageKey, Object... args) {
        super(messageKey);
        this.status = status;
        this.messageKey = messageKey;
        this.args = args;
    }

    /** بيرجع كود الـ HTTP المقترح للرد. */
    public int getStatus() { return status; }

    /** بيرجع مفتاح الرسالة في ملفات الترجمة. */
    public String getMessageKey() { return messageKey; }

    /** بيرجع معاملات الرسالة (مثل {0}). */
    public Object[] getArgs() { return args; }
}