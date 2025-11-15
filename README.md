New plan. Working on another project allowed me to rethink the way I approach the implementations
in this new "architecture".\
Now I have classes-functions called Actions. Basically they're layouts of how functions (Actions)
that they depend on should be called. E.g. and Action called "Example" should run Action dep1 with
it's input, then pass the result to dep2 and dep3 ran in parallel, and then the result should be
passed to a dep4 run in an update loop.\
All these class functions should be interconnected via some kind of DI which I called Assemblies
in an honor to .NET. Each Action has a corresponding Assembly function. However, Actions are
dependant on other Actions, but Assemblies are allowed to depend on exact implementations and call
other Assemblies. I'm not quite sure if depending of Assemblies on exact Actions is ok, but I'll
leave the final decision to the future self.\
Working a while on that project allowed me to build a diagram showing the logic of that project.
What I want to do now is to migrate that diagram to my own tool and make this tool to generate the
code for me from the diagram. It'll ultimately allow me to develop my "coding" tool while working
on another project.\
The question is where should I start?\
I guess I need to understand the data model of the diagram and/or Actions and Assemblies.
First step will be to focus on the top level function (EntryPoint) and describe it's layout. Then
figure out how could I generate a diagram from it. Then I need a way to edit this diagram, what
should also change the data model. Later I should figure out how to generate Actions and Assemblies
from it.\
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
something goes wrong.\
So the data model is:\
-EntryPoint: Action\
--input: infer\
--output: infer\
--body:\
---"retry until result" (input, output)\
----"repeat until active" (input, Nothing)\
-----"login" (input, output1)\
-----"process anonymous state" (output1, output2)\
-----"process authorised state" (output2, output3)"\
The next step is to draw it using a tool that I haven't created yet.

The plan is to define a color theme: I would like it to be neon-like and will ask Gemini to create
one for me. Then I'll try to create data model and render it.

I created the data model. I don't like that ActionLayout.Sequential may have empty list, but I'll
leave it for the future. I've associated ActionDefinitions into a map structure, so it'll be easier
to refer them in the future. I also will add a "current" action: the one the editor should pickup
from the list of actions and strat to render as a root action. Probably I need to add dropdown list
of all the actions to allow a user to choose one.
I think that an ActionDefinition should be a rectangle, it's width should be defined by it's layout
and be a multiple of some value in dp, e.g. 48dp, let's call it a "step". Thus the hight will be
one "step", width will be body plus two (one in the beginning and one in the end) etc.
I'm stuck a bit because of height calculations. It's analysis paralysis. Do I even need to calculate
the height? I think I do. The idea is to represent the diagram as a vertical list of rows, where
each row will draw it's own elements. I also need to know an element's offset at each row. Let's
start simple: I need to draw top-level rectangle with it's name withing. Then I need to draw all
other "layers".
I just realised: I just need to do what I just described - translate an action into "sparse table",
which will contain of "spaces" and rectangles. Should it be a recursive function?

Ok, I did it almost properly. Sequential layout misses internal spaces and I definitely need to add
horizontal scrolling, but it works. So the next steps are to fix these mistakes and add mutability,
because I figured out (again) that immutable data structures are not good for editors, which are
basically just more complex input forms.
I've added horizontal scroll. It is working. I don't like light theme though.
Next: focus on editability (how to add new elements), expandability (how to expand actions) and fix
those spaces.

First step on editability - tap-to-add. When there's nothing on the screen click should add a new
ActionDefinition.
I made everything mutable and realized that probably ActionDefinition fields should be states as
well.
Ok, after fixing couple of bugs I was able to add the first ActionDefinition by click.
The next step is to make the name editable and to add a way to add new functions to the body of
ActionDefinition.

Decided to get rid of input and output types, because I'm not don't anything with them right now.
Also I'll add an id to action, so each ActionDefinition will be able to have a persistent id, while
having a mutable name.
I've added name editing. It is awful now: editing view have internal paddings what makes text
cropped, the font color is grey and look weird, no value checks so you could save an empty value or
the same value as exists, and no save-on-focus-loss. But I'll address it in the future maybe.
Next I need to make it possible to add new functions into an ActionDefinition body.

First of all: the body should be mutable and nullable. This will allow me to not deciding on what
should be there in the beginning and edit it in the future. I'll also fix the error with new
nullable type for ActionDefinition.width: let it be one by default.
I'm stuck: I was "rendering" ActionDefinition hierarchy into a kind of a grid or table that I easily
can draw later, but now, when ActionDefinition.body became a MutableState I realised that I have to
listen to the item in order to redraw it when something changed. This is painful.
I'm thinking if it is possible to traverse through a tree and emit a sequence of "rows". It is
called breadth-first traversal as far as I remember. However, width calculation should be DFS.
Ok, I failed for today. It feels like the solution is near, but I'm tired and cannot proceed. I also
worried that having mutable states without remember() will not have any reasonable effect. Anyway,
the first step is to convert the RenderTable into a sequence of lists using BFS for traversing on
demand.

I have a thought: Compose is saying that it is optimised, so maybe I shouldn't really bother myself
with all these conversion to a table or a sequence and I can just render the table? Yes, it'll be a
repeated hierarchy of rows and columns, but I'll let Compose handle it.
Will force-enable the dark theme, as the light one is burning my eyes.
Oh wow, theme is ignored completely for some reason...
Cool, I managed to add "addition" for ActionDefinition and a button in it's empty body to open a
stub dropdown menu (which is in dark theme, btw). Next need to populate it with meaningful items and
add "addition" logic for all the ActionLayouts types.
I quickly googled the dark theme problem and it appears that I need to wrap everything into
Scaffold. How weird...

Now I can add new "functions" to a ActionDefinition. Added all of them. Not reliable as I manually
iterate over them. Now I need to be able to add RepeatWhileActive (renamed from RepeatUntilActive)
into RetryUntilResult.
Hey, I extracted TextBlock and ActionLayoutSelector, and creation of RetryUntilResult.render was a
piece of cake! Now I'll add RepeatWhileActive and ActionLayout.Action as they should be pretty
straightforward, and then will try to figure out the a to do Sequential.
Completed them and also tweaked some stuff. Time to commit. Had a thought: what if I'll rewrite this
document on each commit. So it'll be possible to read only the thoughts related to the exact commit,
rather than everything at once.
Quick thought: need to add a way to delete Actions, save and load them.
However, next step is Sequential.

Sequential is just a row of items. But how to show that you can add something to it? Well, an "add"
button obviously. Let's do it.
Well it works, kind of. I faced weird bug: when I'm clicking the plus button and adding the second
Action, the first one gets stretched and the second one is squished.
Damn it! I'm using fillMaxWidth() for TextBlocks...
Not sure what to do. Cannot just remove it. Also should prohibit addition of Sequential to
Sequential. Will leave it for tomorrow.

Fixed the problem by explicitly setting Modifier.width(IntrinsicSize.Max) when rendering an item of
Sequential.
Hey, I did it! Can render Sequential properly. Now it'll be nice to save the tree.

Need to fix the bug with the ActionLayout selection dialog not disappearing when selection is made
in Sequential element.
Simple: just explicitly "close" is when an item is selected.

Quick fix: sequential is looking better, I think. The plus button is not that far away from the
other items.
I think I need to convert this to a plugin for Intellij IDEA. It'll be easier to generate/modify
kotlin code using IDE's capabilities.