/*
 * HelloWorld6.java: Hello, World example
 * $Id: HelloWorld6.java,v 1.3 2001-10-20 00:16:11 karczma Exp $
 */

import streamit.*;

class HelloWorld6 extends StreamIt
{
    static public void main (String [] t)
    {
        HelloWorld6 test = new HelloWorld6 ();
        test.run ();
    }

    public void init ()
    {
        add (new Filter ()
        {
            int x;
            Channel output = new Channel (Integer.TYPE, 1);    /* push */
            public void init() {
                this.x = 0;
            }
            public void initIO ()
            {
                streamOutput = output;
            }

            public void work ()
            {
                output.pushInt (x++);
            }
        });
        /* add (new AmplifyByN (3)); */
        add (new Filter ()
        {
            Channel input = new Channel (Integer.TYPE, 1);     /* pop [peek] */

            public void initIO ()
            {
                streamInput = input;
            }

            public void work ()
            {
                System.out.println (input.popInt ());
            }
        });
    }
}

