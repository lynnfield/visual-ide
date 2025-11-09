New plan. Working on another project allowed me to rethink the way I approach the implementations
in this new "architecture".
Now I have classes-functions called Actions. Basically they're layouts of how functions (Actions)
that they depend on should be called. E.g. and Action called "Example" should run Action dep1 with
it's input, then pass the result to dep2 and dep3 ran in parallel, and then the result should be
passed to a dep4 run in an update loop.
All these class functions should be interconnected via some kind of DI which I called Assemblies
in an honor to .NET. Each Action has a corresponding Assembly function. However, Actions are
dependant on other Actions, but Assemblies are allowed to depend on exact implementations and call
other Assemblies. I'm not quite sure if depending of Assemblies on exact Actions is ok, but I'll
leave the final decision to the future self.
Working a while on that project allowed me to build a diagram showing the logic of that project.
What I want to do now is to migrate that diagram to my own tool and make this tool to generate the
code for me from the diagram. It'll ultimately allow me to develop my "coding" tool while working
on another project.
The question is where should I start?
I guess I need to understand the data model of the diagram and/or Actions and Assemblies.
First step will be to focus on the top level function (EntryPoint) and describe it's layout. Then
figure out how could I generate a diagram from it. Then I need a way to edit this diagram, what
should also change the data model. Later I should figure out how to generate Actions and Assemblies
from it.
Let's start with setting up the project, then describing the function, then rendering it.
I've finished with setting up the project (decided to create a new one to save some time).
Now it is time to describe the function. Where should I start? It has name, input and output
parameters, dependencies and several "layout" functions that describe how dependencies are
interconnected with each other. In the current case the dependencies are called "login", "process
anonymous state" and "process authorised state". They are called sequentially passing their outputs
to the next one. This call sequence is wrapped into "repeat until active" aka infinite loop, which
is wrapped into "retry until result" - a function that keeps looping while the internal one is
throwing exceptions and returning otherwise. The trick is that "repeat until active" returns
Nothing. What makes "retry until result" + "repeat until active" pair to run forever even when
something goes wrong.
So the data model is:
    RepeatUntilActive: Action
        input: infer
        output: infer
        body:
            "retry until result" (input, output)
                "repeat until active" (input, Nothing)
                    "login" (input, output1)
                    "process anonymous state" (output1, output2)
                    "process authorised state" (output2, output3)"
The next step is to draw it using a tool that I haven't created yet.