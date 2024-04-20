package com.hmdp.work;

import java.util.Random;

interface Condition {
    boolean test(int value);
}

//设定最小值和最大值
class Subject {
    private int value = 0;
    private final int maxValue;

    public Subject(int maxValue) {
        this.maxValue = maxValue;
    }

    public int getValue() {
        return value;
    }

    public void increment() {
        if (value < maxValue) {
            value++;
        }
    }
}

class Observer {
    private final String name;
    private final Subject subject;
    private final Condition condition;
    private final int maxValue;

    public Observer(String name, Subject subject, Condition condition, int maxValue) {
        this.name = name;
        this.subject = subject;
        this.condition = condition;
        this.maxValue = maxValue;
    }

    public void update(int currentValue, String action) {
        if (currentValue < maxValue && condition.test(currentValue)) {
            System.out.println(name + " requested increment at " + currentValue + " for " + action);
            synchronized (subject) {
                if (currentValue < maxValue && condition.test(currentValue)) {
                    subject.increment();
                    System.out.println(name + " 当前值为: " + subject.getValue());
                }
            }
        } else {
            System.out.println(name + " is still in the competition");
        }
    }
}

public class Main {
    public static void main(String[] args) {
        Subject subject = new Subject(100);

        Observer observer1 = new Observer("线程1", subject, value -> value % 2 != 0, 70);
        Observer observer2 = new Observer("线程2", subject, value -> true, 80);
        Observer observer3 = new Observer("线程3", subject, value -> value % 2 == 0, 40);
        Observer observer4 = new Observer("线程4", subject, value -> true, 100);

        Thread thread1 = new Thread(() -> {
            while (subject.getValue() < 70) {
                observer1.update(subject.getValue(), "单数加一");
            }
        });

        Thread thread2 = new Thread(() -> {
            while (subject.getValue() < 80) {
                observer2.update(subject.getValue(), "随机增加");
            }
        });

        Thread thread3 = new Thread(() -> {
            while (subject.getValue() < 40) {
                observer3.update(subject.getValue(), "双数加一");
            }
        });

        Thread thread4 = new Thread(() -> {
            while (subject.getValue() < 100) {
                observer4.update(subject.getValue(), "随机增加");
            }
        });

        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();
    }
}
