SerialParser
========

SerialParser is a small library that can be used to detect specific data frames in a byte array, e.g. while reading a
stream.

Currently SerialParser can be used to detect:
 - Fixed length data frames with one or more header bytes and an optional terminating byte
 - Variable length data frames bounded by one or more header bytes and one terminating byte.

Note: detecting variable length frames require an explicitly set buffer size large enough to hold the longest matched frame.
This means that the largest variable size frame detected can have the length of the buffer size, including the framing bytes.

Example
---------

The following example will call the listener object with "matched" as the second argument (converted to a byte array).

    SerialParser.FrameMatchListener listener = new SerialParser.FrameMatchListener() {
                public void onFrameMatched(SerialParser.FrameDefinition frame, byte[] data) {
                    //data will be byte[]{'m', 'a', 't', 'c', 'h', 'e', 'd'}
                }
            };

    SerialParser parser = new SerialParser
                                 .Builder()
                                 .setBufferSize(9)  //large enough to match "+matched;"
                                 .addFrameDefinition(
                                         new SerialParser.FrameDefinition(1, "+")
                                                 .setTerminatingByte((byte) ';')
                                                 .addListener(listener)
                                 )
                                 .build();
    parser.add("not matched text +matched;".getBytes());

Installation
------------
SerialParser is available as a Maven repository through jitpack.io

## As a Gradle dependency

    repositories {
        maven { url "https://jitpack.io" }
    }

    dependencies {
        compile 'com.github.bugadani:SerialParser:d00b6f2'
    }

## Maven

    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>

    <dependency>
        <groupId>com.github.bugadani</groupId>
        <artifactId>SerialParser</artifactId>
        <version>d00b6f2</version>
    </dependency>
