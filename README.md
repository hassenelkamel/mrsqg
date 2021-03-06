MrsQG is a semantics-based question generation system for single English sentences. For instance, given a sentence "Kim gave Sandy a cat", MrsQG generates the following questions:

  * 4.50: To what did Kim give a cat?
  * 4.37: What gave Sandy a cat?
  * 3.72: Who gave Sandy a cat?
  * 2.85: Which animal did Kim give Sandy?
  * 2.73: To who did Kim give a cat?
  * 2.30: Kim gave Sandy a cat?


## Features ##

  * Question types: who, what, when, where, which, how, how many/much and yes/no
  * Deep parsing with [PET](http://wiki.delph-in.net/moin/PetTop)
  * Chart generation with [LOGON](http://wiki.delph-in.net/moin/LogonTop)/[LKB](http://wiki.delph-in.net/moin/LkbTop)
  * The [English Resource Grammar](http://lingo.stanford.edu/erg.html) as the backbone
  * [Minimal Recursion Semantics](http://www.cl.cam.ac.uk/~aac10/papers/mrs.pdf) as the theory support
  * Output ranking with maximum entropy and language models
  * PList output to [NPCEditor](http://vhtoolkit.ict.usc.edu/index.php/NPCEditor) for restricted-domain question answering systems

## System Requirement ##

  * 5 GB disk space
  * 4~16 GB RAM
  * Linux Operating System
  * Java 1.6

## Installation ##

MrsQG is written in Java  -- compilation and installation are easy. But it calls PET (C++) to do parsing and LOGON/LKB (Common Lisp) to do generation, thus users need to install these two first.

## Compile PET ##

PET can be obtained from [PetTop](http://wiki.delph-in.net/moin/PetTop). Compiling PET from sources is needed.

MrsQG runs PET in server mode. A unique signal is needed after PET has output the parsing results. Thus after obtaining the source code of PET, users need to modify the file cheap/cheap.cpp:

Locate to the end of the function interactive() and add the following line before fflush(fstatus);:
```
 fprintf(fstatus, "cheap is brutally patched\n");
```
so the overall area looks like this:
```
    fprintf(fstatus, "cheap is brutally patched\n");
    fflush(fstatus);

    if(Chart != 0) delete Chart;

    id++;
  } /* while */
```
Then follow the normal procedure to install PET.

## Install LOGON/LKB ##

LOGON is able to give ranked output with an MaxEnt model so it is recommended over LKB.

To install LOGON, follow [LogonInstallation](http://wiki.delph-in.net/moin/LogonInstallation).

To install LKB, follow [LkbInstallation](http://wiki.delph-in.net/moin/LkbInstallation). The 'test' version rather than 'stable' or 'latest' version is required.

Only either LOGON or LKB is needed. So users don't have to install both. When installing LOGON/LKB, ERG is also automatically installed. Users need to locate the grammar file "english.grm" for later usage.

## Install MrsQG ##

Go to [Downloads](http://code.google.com/p/mrsqg/downloads/list) and obtain the main program file MrsQG-version.tar.bz2 and the two resource files res1.tar.bz2, res2.tar.bz2. Put them into the same directory, then run:
```
$ tar xjf MrsQG-1.0.tar.bz2

$ tar xjf res1.tar.bz2

$ tar xjf res2.tar.bz2

$ mv res MrsQG-1.0

$ cd MrsQG-1.0

$ ant
```
The two resource files contain some version independent models and should be placed under the top directory of MrsQG.

Java 1.6 is needed to compile/run MrsQG.

## Configure MrsQG ##

Go to the "conf" directory. There should be 5 configuration files:

  * log4j.properties: for logging, you can leave it untouched

  * mrsqg.properties: for overall configuration, you can leave it untouched if you have more than 7GB RAM.

  * cheap.properties: for configuring the PET parser. You should modify the location for the executable "cheap" and the grammar file "english.grm"

  * lkb.script: initial loading script for LOGON/LKB. You should modify the location for the ERG script file.

  * lkb.properties: for configuring the LOGON/LKB generator. You should modify the location for the executable "logon" or "lkb" and the lkb script file lkb.script as described above

## Run MrsQG ##

$ ./run.sh

The above command runs MrsQG, if everything is configured correctly. If you are using LKB, then the script usually starts faultlessly. However, logon sometimes doesn't start in the right way. If you observer the following output:
```
  Restart actions (select using :continue):
   0: Try calling READ-SCRIPT-FILE-AUX again.
   1: Try calling LKB:READ-SCRIPT-FILE-AUX instead.
   2: Return a value instead of calling READ-SCRIPT-FILE-AUX.
   3: Try calling a function other than READ-SCRIPT-FILE-AUX.
   4: Setf the symbol-function of READ-SCRIPT-FILE-AUX and call it again.
   5: Return to Top Level (an "abort" restart).
   6: Abort entirely from this (lisp) process.
  [1] TSNLP(2): 
```

Then press Ctrl-C to stop and run again. An indication of a successful start should be:

```
  [changing package from "COMMON-LISP-USER" to "TSDB"]
  TSNLP(1): 
  LKB(2): 
```

Then wait patiently until you see the usage message.

## Use MrsQG ##

MrsQG prints the following usage information:
```
  Usage:

        1. a declarative sentence.
                MrsQG generates a question through pipelines of PET and LKB.
        2. pre: a sentence.
                MrsQG generates the pre-processed FSC in XML. Then you can copy/paste this FSC into cheap to parse.
        3. mrx: an declrative MRS XML (MRX) file.
                MrsQG reads this MRX and transforms it into interrogative MRX.
                Then you can copy/paste the transformed MRX to LKB for generation.
        4. lkb: an LKB command
                Then MrsQG serves as a wrapper for LKB. You can talk with LKB interactively through the prompt.
        5. pet: a sentence.
                Then MrsQG serves as a wrapper for cheap. You can talk with cheap interactively through the prompt.
        6. pg: a sentence.
                Then MrsQG first parses then generates from the sentence (pg stands for Parse-Generate).
        7. file: input.txt output.xml
                Then MrsQG g enerate questions from the text of input.txt and output to output.xml (used by plist of NPCEditor)
        8. dryrun: input.txt output.xml
                similar to 7, but only do parsing and transformation to give a quick pass of all sentences. Used to check errors.
        9. help (or h)
                Print this message.
```

## Memory consumption of MrsQG ##

Here's a memory estimation of different component of MrsQG:

  * PET: 0.5GB~4GB

  * LOGON/LKB: 0.5GB~4GB

  * MrsQG without language model: 0.7GB

  * MrsQG with the small language model (q.lm.gz): 1.5GB

  * MrsQG with the large language model (q.wiki.plm.gz): 4.5GB

Thus to run MrsQG, at least 2GB RAM is needed. 8GB~16GB is preferred. PET/LOGON/LKB's memory usage goes up as the input sentence length increases. The language model package employed by MrsQG is not very memory efficient, thus it consumes a lot of memory even loading a relatively-small language model file.
