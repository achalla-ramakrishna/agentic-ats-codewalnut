package com.codewalnut.ats.bank;

import com.codewalnut.ats.client.InterviewAgent.DraftQuestion;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written AI interview sets for a software engineering intern with front-end and Java skills (freshers:
 * MCA / B.E. / M.Sc. students), written like a campus interview loop: introduction and projects, web, React and
 * Java fundamentals with code-output questions, two coding problems, a small front-end design question from
 * airline booking, teamwork and AI tools, and availability. Four sets (A–D) cover the same ground with different
 * questions. Marked answers were checked by hand; spoken questions carry what a good answer covers.
 */
public final class FrontEndInternSets {

    private FrontEndInternSets() {}

    public static List<DraftQuestion> set(int set) {
        return switch (set) {
            case 2 -> setB();
            case 3 -> setC();
            case 4 -> setD();
            default -> setA();
        };
    }

    // ---- Set A: event loop basics, React state, String equality, brackets, debounce, flight search page ----

    static List<DraftQuestion> setA() {
        List<DraftQuestion> q = new ArrayList<>();
        q.add(intro());
        q.add(video("EXPERIENCE", "Tell us about an internship, freelance work or a team project at college. What was your role, "
                + "what did you build yourself, and how did the team split the work?", 120,
                "A specific contribution in their own words (\"I built…\", not only \"we\"); the tools used; how work was split and "
                        + "tracked (Git, tasks); something they learned. Honest if it was only coursework."));
        q.add(video("PROJECTS", "Pick the project on your résumé you know best. Explain how it works end to end: the screens, the "
                + "API calls, and where the data is stored. Which parts did you write yourself?", 150,
                "Can trace a user action from the UI through the API to the database and back; names real files, components "
                        + "or endpoints; clear about their own part. Vague buzzwords or only listing technologies is weak."));
        q.add(video("PROJECTS", "In that project, what was the hardest bug you fixed? How did you find the cause, and how did you "
                + "check that the fix worked?", 120,
                "A concrete bug; a method (reproduce, read the error/stack trace, console or debugger, network tab, narrow it "
                        + "down); the real cause, not just the symptom; how they verified (re-test, a test case)."));
        q.add(blank("In JavaScript, the operator that compares both value and type, without converting either, is ____.", "==="));
        q.add(blank("In CSS, to stack the children of a flex container vertically, set flex-direction: ____;", "column"));
        q.add(blank("In React, the hook that stores a value between renders and re-renders the component when it changes is ____.",
                "useState", "useState()"));
        q.add(blank("In Java, a class inherits from another class with the keyword ____.", "extends"));
        q.add(blank("In SQL, to remove duplicate rows from a result, write SELECT ____ city FROM students;", "DISTINCT"));
        q.add(blank("The Git command that copies a remote repository to your computer for the first time is git ____.", "clone"));
        q.add(choiceWithCode("What does this print?", "console.log(typeof null);",
                List.of("\"null\"", "\"object\"", "\"undefined\"", "It throws an error"), 1));
        q.add(choiceWithCode("What does this print?", """
                for (var i = 0; i < 3; i++) {
                  setTimeout(() => console.log(i), 0);
                }""", List.of("0 1 2", "3 3 3", "0 0 0", "undefined three times"), 1));
        q.add(choice("A React list renders items with key={index}, and users can delete items from the middle. What can go wrong?",
                List.of("Nothing; index keys are always fine",
                        "React may keep the wrong item's state (such as a ticked checkbox or typed text) on the remaining items",
                        "The list will not render at all",
                        "Keys only affect speed, never behaviour"), 1));
        q.add(choiceWithCode("What does this Java code print?", """
                String a = "hello";
                String b = new String("hello");
                System.out.println(a == b);
                System.out.println(a.equals(b));""", List.of("true, true", "false, true", "true, false", "false, false"), 1));
        q.add(choice("Which CSS selector has the highest specificity?", List.of("div p", ".card p", "#main p", "p"), 2));
        q.add(choice("A REST API creates a new booking. Which method and success status fit best?",
                List.of("GET, 200 OK", "POST, 201 Created", "PUT, 204 No Content", "DELETE, 200 OK"), 1));
        q.add(choiceWithCode("Tables: students(id, name) and marks(student_id, score). Which query lists every student, including "
                + "students who have no marks yet?", "", List.of(
                "SELECT s.name, m.score FROM students s INNER JOIN marks m ON s.id = m.student_id",
                "SELECT s.name, m.score FROM students s LEFT JOIN marks m ON s.id = m.student_id",
                "SELECT s.name, m.score FROM marks m LEFT JOIN students s ON s.id = m.student_id",
                "SELECT name, score FROM students, marks"), 1));
        q.add(choice("What is the time complexity of binary search on a sorted array of n items?",
                List.of("O(1)", "O(log n)", "O(n)", "O(n log n)"), 1));
        q.add(video("TECHNICAL", "Explain, step by step, what happens from typing www.example.com into the browser until you see the "
                + "page.", 120,
                "DNS lookup; TCP and TLS connection; HTTP request and response; parsing HTML, fetching CSS/JS/images; building the "
                        + "DOM and CSSOM; layout and paint; running JavaScript; caching. Order and clarity matter more than every detail."));
        q.add(video("TECHNICAL", "In React, a click handler calls setCount(count + 1) three times, but the count only goes up by one. "
                + "Why, and how would you fix it?", 90,
                "Each call uses the same count from that render (closure); updates are batched; fix with the updater form "
                        + "setCount(c => c + 1). Bonus: state updates are not immediate, so reading count right after shows the old value."));
        q.add(video("TECHNICAL", "In Java, what is the difference between an abstract class and an interface? Give an example of when "
                + "you would choose each.", 90,
                "Abstract class: shared state and code, single inheritance, constructors; interface: a contract, many can be "
                        + "implemented, default methods since Java 8. A sensible example (e.g. Payment interface with Card/UPI; "
                        + "abstract Vehicle with shared fields)."));
        q.add(writtenCode("Write a function, in Java or JavaScript, that checks whether a string of brackets such as \"{[()]}\" is "
                + "balanced, returning true or false. Then state its time complexity in one line.",
                "Uses a stack: push opening brackets; on a closing bracket, pop and check it matches; balanced only if the stack "
                        + "is empty at the end. Handles \"(]\", \")(\" and \"((\". O(n) time, O(n) space. Counting brackets alone is wrong."));
        q.add(writtenCode("Write a JavaScript function debounce(fn, delay) that returns a new function which calls fn only after "
                + "delay milliseconds have passed without another call. Show how you would use it on a search box.",
                "A closure holding a timer: clearTimeout on each call, then setTimeout(() => fn(...args), delay); passes the "
                        + "arguments through. Usage: input.addEventListener('input', debounce(search, 300)) or a React onChange, so "
                        + "the API is not called on every key."));
        q.add(video("ARCHITECTURE", "Design the front end of a flight search page. The user enters from, to and date, and sees a list "
                + "of flights with filters for price, stops and airline. Which components would you create, where does the state "
                + "live, and how do you handle loading, errors and a slow API?", 150,
                "Components such as SearchForm, Filters, ResultsList, FlightCard; state in a common parent or the URL (shareable "
                        + "searches); loading, empty and error states; disable double submit; ignore or cancel stale responses "
                        + "(AbortController); filter on the client or the server and why; pagination or virtualised list for many "
                        + "results; accessibility."));
        q.add(choice("ARCHITECTURE", "Your page calls an API on another domain and the browser blocks it with a CORS error. Where is "
                + "it fixed?", List.of("In the React code, by adding a header to fetch",
                "On the server, by allowing your site's origin (Access-Control-Allow-Origin)",
                "By switching from fetch to axios", "By using HTTPS instead of HTTP"), 1));
        q.add(aiTools("How do you use AI tools such as ChatGPT, Copilot or Claude when you code? Tell us about a time an AI tool gave "
                + "you wrong code: how did you notice, and what did you do?"));
        q.add(video("SOFT_SKILLS", "Tell us about a time a teammate did not do their part on a group project close to a deadline. What "
                + "did you do, and what happened?", 90,
                "Talked to the teammate first; adjusted the plan or split work; raised it early and respectfully; owns the "
                        + "outcome without blaming; what they would do differently."));
        readiness(q);
        return q;
    }

    // ---- Set B: string coercion, event loop order, useEffect, references, box model, first unique char, fetch ----

    static List<DraftQuestion> setB() {
        List<DraftQuestion> q = new ArrayList<>();
        q.add(intro());
        q.add(video("EXPERIENCE", "Describe a time you had to learn a new framework or language quickly, for a project, an internship "
                + "or a hackathon. How did you learn it, and what did you build with it?", 120,
                "A real example; a learning method (official docs, small experiments, building something, asking for help); "
                        + "what they shipped; honest about gaps."));
        q.add(video("PROJECTS", "Describe your best project's structure in words: front end, back end, database and hosting. Why did "
                + "you choose that stack, and what would you change now?", 150,
                "A coherent architecture; reasons for choices (not just \"popular\"); a sensible change with hindsight; which "
                        + "parts they built."));
        q.add(video("PROJECTS", "If 1,000 people used that project at the same time tomorrow, what would break first, and how would "
                + "you find out?", 120,
                "Concrete guesses (database queries, no pagination, server size, rate limits, images); how to find out (load "
                        + "test, logs, monitoring, browser performance tools); a first fix. Saying \"nothing\" is weak."));
        q.add(blank("In JavaScript, to make a new array by applying a function to every element, use the array method ____.", "map",
                "map()", ".map"));
        q.add(blank("In CSS, the property that sets the space between an element's content and its border is ____.", "padding"));
        q.add(blank("In React, each item in a rendered list needs a unique ____ prop.", "key"));
        q.add(blank("In Java, a field shared by all objects of a class is declared with the keyword ____.", "static"));
        q.add(blank("In SQL, to filter groups after GROUP BY (for example COUNT(*) > 5), use ____.", "HAVING"));
        q.add(blank("To create a new branch and switch to it, run git ____ -b feature-x.", "checkout"));
        q.add(choiceWithCode("What do these two lines print?", """
                console.log(1 + "2" + 3);
                console.log(1 + 2 + "3");""", List.of("\"123\" and \"33\"", "6 and 6", "\"123\" and \"123\"", "\"15\" and \"33\""), 0));
        q.add(choiceWithCode("In what order are the letters printed?", """
                console.log("A");
                setTimeout(() => console.log("B"), 0);
                Promise.resolve().then(() => console.log("C"));
                console.log("D");""", List.of("A B C D", "A D C B", "A D B C", "A C D B"), 1));
        q.add(choice("useEffect(() => { loadFlights(); }) is written with no dependency array. When does it run?",
                List.of("Once, after the first render", "After every render", "Only when the component unmounts",
                        "Never, without a dependency array"), 1));
        q.add(choiceWithCode("What does this Java code print?", """
                int[] a = {1, 2, 3};
                int[] b = a;
                b[0] = 9;
                System.out.println(a[0]);""", List.of("1", "9", "0", "It does not compile"), 1));
        q.add(choice("Which Java collection fits best for storing unique email addresses and checking quickly whether one exists?",
                List.of("ArrayList", "HashSet", "LinkedList", "An array"), 1));
        q.add(choice("An element has width: 200px; padding: 10px; border: 2px solid; and the default box-sizing. How wide is it "
                + "on screen?", List.of("200px", "224px", "220px", "212px"), 1));
        q.add(choice("A user asks for a booking that does not exist. Which status should the API return?",
                List.of("200 OK", "400 Bad Request", "404 Not Found", "500 Internal Server Error"), 2));
        q.add(choiceWithCode("What does this query return?", "SELECT department, COUNT(*) FROM employees GROUP BY department;",
                List.of("The total number of employees", "One row per department, with how many employees it has",
                        "Every employee with their department", "An error, because COUNT needs a column name"), 1));
        q.add(video("TECHNICAL", "Explain the difference between var, let and const in JavaScript, with an example of a bug that var "
                + "can cause.", 90,
                "var is function-scoped and hoisted (undefined before assignment); let and const are block-scoped with a "
                        + "temporal dead zone; const stops reassignment, not changes inside an object; the classic loop-with-"
                        + "setTimeout bug or a leaked variable."));
        q.add(video("TECHNICAL", "What happens in React when a component's state changes? Where does the virtual DOM fit in?", 90,
                "The component and its children re-render (run again); React compares the new output with the previous one "
                        + "(reconciliation) and updates only the changed DOM nodes; keys help match list items; memo or moving "
                        + "state down avoids needless re-renders."));
        q.add(video("TECHNICAL", "In Java, explain method overloading and method overriding, with a small example of each.", 90,
                "Overloading: same name, different parameters, chosen at compile time (add(int,int) and add(double,double)); "
                        + "overriding: a subclass replaces a parent method with the same signature, chosen at run time "
                        + "(polymorphism); @Override."));
        q.add(writtenCode("Write a function, in Java or JavaScript, that returns the first character of a string that does not "
                + "repeat, for example \"swiss\" → 'w'. Return null if there is none. State its time complexity.",
                "Counts characters in a map in one pass, then finds the first with count 1 in a second pass: O(n). Handles an "
                        + "empty string and all-repeating input. Nested loops (O(n²)) work but are weaker."));
        q.add(writtenCode("Using fetch (or axios) with async/await, write a function that loads flights from "
                + "/api/flights?from=BLR&to=DEL and returns the list. Handle a network failure and a non-200 response "
                + "separately.",
                "async function with try/catch; checks response.ok and throws or returns an error with the status; parses "
                        + "JSON; distinguishes the network error (catch) from an HTTP error (status); maybe a timeout with "
                        + "AbortController; no unhandled promise."));
        q.add(video("ARCHITECTURE", "Design a seat selection screen: a seat map where the user picks a seat and confirms. Two people "
                + "might pick the same seat at the same moment. What does the front end show, and what must the back end do so "
                + "the seat is not booked twice?", 150,
                "Seat map component with selected and taken states; refreshes availability; the back end checks and books "
                        + "atomically (a transaction or unique constraint, or a hold with expiry) and returns an error such as "
                        + "409 Conflict to the loser; the front end shows a clear message and refreshes the map. Front-end checks "
                        + "alone are not enough."));
        q.add(choice("ARCHITECTURE", "A list of 10,000 flights makes the page slow to scroll. What helps most?",
                List.of("Put the list in a div with overflow: scroll", "Render only the visible rows (virtualisation) or paginate",
                        "Use var instead of let", "Move the CSS into the JavaScript file"), 1));
        q.add(aiTools("Suppose an AI tool writes a 50-line React component for you. What do you check before you commit it?"));
        q.add(video("SOFT_SKILLS", "Tell us about feedback on your work that you disagreed with. What did you do?", 90,
                "Listened and asked why; explained their view with evidence; changed their mind or agreed a way forward; "
                        + "stays respectful; learned something."));
        readiness(q);
        return q;
    }

    // ---- Set C: object references, map/filter, controlled inputs, list removal, HashMap, two sum, search filter ----

    static List<DraftQuestion> setC() {
        List<DraftQuestion> q = new ArrayList<>();
        q.add(intro());
        q.add(video("EXPERIENCE", "Tell us about a hackathon, open-source contribution or club project. What did you contribute, and "
                + "what did you learn about working in a team?", 120,
                "Their own contribution; working with others under time pressure; a lesson about teamwork or code (Git "
                        + "conflicts, splitting work, communication)."));
        q.add(video("PROJECTS", "Explain a project where your front end calls an API. How does data get from the database to the "
                + "screen? Which parts did you write?", 150,
                "Traces the request: UI event, fetch call, endpoint, query, response, state update, render; names the actual "
                        + "pieces; clear ownership."));
        q.add(video("PROJECTS", "How did you test that project? If you did not write tests, which two tests would you write first, "
                + "and why?", 120,
                "Manual and automated testing they really did; sensible first tests (a key function's edge cases, an API "
                        + "endpoint's success and error, a form validation); why those protect the most important behaviour."));
        q.add(blank("In JavaScript, a function that remembers variables from the scope where it was created is called a ____.",
                "closure"));
        q.add(blank("In HTML, the attribute that gives an image a text description for screen readers is ____.", "alt"));
        q.add(blank("In React, the values a parent passes to a child component are called ____.", "props"));
        q.add(blank("In Java, calling a method on a null reference throws a ____.", "NullPointerException", "NPE"));
        q.add(blank("In SQL, the constraint that uniquely identifies each row of a table is the PRIMARY ____.", "KEY"));
        q.add(blank("To save your staged changes to the local Git history, run git ____ -m \"message\".", "commit"));
        q.add(choiceWithCode("What does this print?", """
                const user = { name: "Asha" };
                const copy = user;
                copy.name = "Ravi";
                console.log(user.name);""", List.of("Asha", "Ravi", "undefined", "TypeError, because user is const"), 1));
        q.add(choiceWithCode("What does this print?", "console.log([1, 2, 3].map(x => x * 2).filter(x => x > 2));",
                List.of("[2, 4, 6]", "[4, 6]", "[1, 2, 3]", "[6]"), 1));
        q.add(choice("In React, <input value={name} /> is rendered with no onChange handler. What happens?",
                List.of("It works normally", "It shows the value but the user cannot type into it (React warns)",
                        "It throws an error and the page crashes", "It clears itself after every key"), 1));
        q.add(choiceWithCode("What happens when this Java code runs?", """
                List<Integer> list = new ArrayList<>(List.of(1, 2, 3));
                for (Integer x : list) {
                    if (x == 1) list.remove(x);
                }""", List.of("It removes 1; the list is [2, 3]", "It throws ConcurrentModificationException",
                "It removes the element at index 1", "It does not compile"), 1));
        q.add(choice("Which statement about Java's String is true?",
                List.of("Strings are mutable; concat changes the original", "Strings are immutable; methods like toUpperCase() "
                        + "return a new String", "== compares the characters of two Strings", "String is a primitive type"), 1));
        q.add(choice("How do you centre a child both horizontally and vertically inside a flex container?",
                List.of("text-align: center", "display: flex; justify-content: center; align-items: center", "margin: center",
                        "position: center"), 1));
        q.add(choice("Which statement about GET requests is correct?",
                List.of("GET is meant to change data on the server", "GET can be cached and should not change data",
                        "GET requests always need a body", "GET is safer than POST for sending passwords"), 1));
        q.add(choice("Which data structure fits an \"undo\" feature best?", List.of("Queue", "Stack", "HashMap", "Binary tree"), 1));
        q.add(video("TECHNICAL", "Explain the difference between == and === in JavaScript, and between null and undefined. Where might "
                + "each show up in a React app?", 90,
                "== converts types before comparing, === does not (prefer ===); undefined is \"not set\" (missing prop, missing "
                        + "field, no return), null is set to \"no value\" on purpose (e.g. no user logged in, data not loaded); "
                        + "optional chaining and default values."));
        q.add(video("TECHNICAL", "Your React page calls the same API twice when it loads. What could cause it, and how would you find "
                + "out?", 90,
                "React StrictMode runs effects twice in development; a dependency array with an object or function that "
                        + "changes every render; two components fetching the same data; the component re-mounting (key change). "
                        + "Find out with the Network tab, logs or React DevTools; fix with correct dependencies, lifting the fetch "
                        + "up or a cache (React Query)."));
        q.add(video("TECHNICAL", "What is a HashMap in Java? How does it find a value by key, and what happens when two keys have the "
                + "same hash code?", 90,
                "Buckets chosen from hashCode(); equals() finds the right key in the bucket; collisions share a bucket (list, "
                        + "or a tree when large); average O(1); keys must keep equals/hashCode consistent and not change."));
        q.add(writtenCode("Write a function, in Java or JavaScript, that takes an array of numbers and a target and returns the "
                + "indexes of two numbers that add up to the target, for example [2, 7, 11, 15] and 9 → [0, 1]. Try to do better "
                + "than checking every pair, and state the time complexity.",
                "One pass with a map from value to index: for each number, check if target − number is in the map; O(n) time "
                        + "and space. Brute force O(n²) works but is weaker. Handles no answer and duplicates (e.g. [3, 3], 6)."));
        q.add(writtenCode("Write a small React component that shows a list of names with a search box that filters the list as the "
                + "user types, ignoring upper and lower case.",
                "useState for the query; a controlled input with onChange; the filtered list worked out from props or state "
                        + "during render (not stored twice); toLowerCase().includes(); key on each item; a message when nothing "
                        + "matches."));
        q.add(video("ARCHITECTURE", "Design a page that shows a passenger's upcoming trips after they log in. How does login work "
                + "between the React front end and the Java back end, where is the login token kept, and how does each request "
                + "prove who the user is?", 150,
                "Login request returns a session cookie or a token (JWT); an httpOnly cookie is safer from XSS than "
                        + "localStorage; each request sends the cookie or an Authorization header; the server checks it and "
                        + "returns only that user's trips (never trusts a user id sent by the page); expiry and logout."));
        q.add(choice("ARCHITECTURE", "Where should the API key for a paid service used by your web app be kept?",
                List.of("In the React code, so the browser can call the service directly", "In a .env file committed to GitHub",
                        "On your server, which calls the service; the browser only calls your server", "In localStorage"), 2));
        q.add(aiTools("Which AI tools do you use while learning or coding, and how do you make sure you still understand the code "
                + "you submit? Give an example."));
        q.add(video("SOFT_SKILLS", "You are given a task with unclear requirements, and your mentor is busy for two days. What do you "
                + "do?", 90,
                "Writes down assumptions and questions; looks for answers in docs, code and teammates; starts with the clear "
                        + "parts or a small prototype; sends a short message with specific questions; does not stay blocked or "
                        + "build the wrong thing for two days."));
        readiness(q);
        return q;
    }

    // ---- Set D: sort, hoisting, immutable state, polymorphism, integer division, reverse words, Booking class ----

    static List<DraftQuestion> setD() {
        List<DraftQuestion> q = new ArrayList<>();
        q.add(intro());
        q.add(video("EXPERIENCE", "Tell us about the most technically difficult thing you have built so far, in an internship, a "
                + "course or on your own. Why was it hard?", 120,
                "A real technical difficulty (not just \"lots of work\"); how they approached it; what they learned; their own "
                        + "part."));
        q.add(video("PROJECTS", "Demo your best project in words: what problem it solves, who uses it, and what the user sees, step "
                + "by step.", 150,
                "Clear problem and user; a walkthrough of the main flow; mentions real features and how they work; their "
                        + "contribution."));
        q.add(video("PROJECTS", "If you had two more weeks on that project, what would you improve first, and why?", 90,
                "Prioritises by user value or risk (a bug, security, performance, tests) rather than adding random features; "
                        + "knows the project's weak points."));
        q.add(blank("In JavaScript, inside an async function, the keyword that waits for a Promise to finish is ____.", "await"));
        q.add(blank("In CSS, to keep a table header visible while its table scrolls, use position: ____;", "sticky"));
        q.add(blank("In React, the hook used to run code after render, such as fetching data, is ____.", "useEffect",
                "useEffect()"));
        q.add(blank("In Java, a class that cannot be extended is declared ____.", "final"));
        q.add(blank("In SQL, to sort from the highest price to the lowest, write ORDER BY price ____.", "DESC"));
        q.add(blank("To bring the latest changes from the remote branch into your local branch, run git ____.", "pull"));
        q.add(choiceWithCode("What does this print?", """
                const nums = [10, 1, 3];
                nums.sort();
                console.log(nums);""", List.of("[1, 3, 10]", "[1, 10, 3]", "[10, 3, 1]", "[3, 1, 10]"), 1));
        q.add(choiceWithCode("What does calling f() print?", """
                function f() {
                  console.log(x);
                  var x = 5;
                }
                f();""", List.of("5", "undefined", "ReferenceError", "null"), 1));
        q.add(choice("Why should you not change state directly, as in items.push(newItem); setItems(items);?",
                List.of("It is slower", "React sees the same array (same reference) and may not re-render",
                        "push does not work on arrays kept in state", "It is fine; React notices the change"), 1));
        q.add(choiceWithCode("What does this Java code print?", """
                class Animal { String sound() { return "..."; } }
                class Dog extends Animal { String sound() { return "Woof"; } }

                Animal a = new Dog();
                System.out.println(a.sound());""", List.of("...", "Woof", "It does not compile", "ClassCastException"), 1));
        q.add(choiceWithCode("What does this Java code print?", """
                int x = 7 / 2;
                System.out.println(x);""", List.of("3.5", "3", "4", "It does not compile"), 1));
        q.add(choice("What is the most accessible way to make a clickable element that runs JavaScript?",
                List.of("<div onclick=\"…\">", "<button type=\"button\">", "<span> styled with cursor: pointer",
                        "<a> with no href"), 1));
        q.add(choice("The API answers a request with 401. What does that usually mean?",
                List.of("The server crashed", "The request is not authenticated (missing or invalid login)",
                        "The resource was not found", "The request body was too large"), 1));
        q.add(choiceWithCode("What is the time complexity of this code?", """
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < n; j++)
                        sum++;""", List.of("O(n)", "O(n²)", "O(log n)", "O(2n)"), 1));
        q.add(video("TECHNICAL", "Explain how async/await works in JavaScript. What happens to the rest of the page while an await is "
                + "waiting for an API response?", 90,
                "async functions return Promises; await pauses only that function; the event loop keeps the page responsive "
                        + "(clicks, rendering); errors via try/catch; independent calls in parallel with Promise.all."));
        q.add(video("TECHNICAL", "What is the difference between props and state in React? Use a flight booking form as your example.",
                90,
                "Props come from the parent and are read-only in the child; state belongs to the component and changes over "
                        + "time; e.g. the form keeps the typed passenger details in state and passes the selected flight as props; "
                        + "lifting state up when siblings need it."));
        q.add(video("TECHNICAL", "In Java, what are checked and unchecked exceptions? Give an example of each, and say how you would "
                + "handle a failure to read a file.", 90,
                "Checked (IOException) must be caught or declared; unchecked (NullPointerException, IllegalArgumentException) "
                        + "are programming errors; try-with-resources to close the file; a helpful message or log, not an empty "
                        + "catch."));
        q.add(writtenCode("Write a function, in Java or JavaScript, that reverses the order of words in a sentence, for example "
                + "\"fly to delhi today\" → \"today delhi to fly\", ignoring extra spaces. State its time complexity.",
                "Trims, splits on one or more spaces, reverses and joins with single spaces (or a two-pointer approach); O(n); "
                        + "handles leading, trailing and repeated spaces and an empty string."));
        q.add(writtenCode("Write a Java class Booking with a passenger name, a seat number and a price, and a method "
                + "applyDiscount(percent). The price must never become negative, and a discount above 100% must be refused.",
                "Private fields; a constructor that validates (no empty name, price ≥ 0); getters; applyDiscount checks 0–100 "
                        + "and throws IllegalArgumentException otherwise; mentions BigDecimal for money as a bonus; "
                        + "encapsulation rather than public fields."));
        q.add(video("ARCHITECTURE", "Design an airport search box: as the user types \"ban\" it suggests \"Bengaluru (BLR)\". How would "
                + "you avoid calling the API on every key, show results quickly, and handle a slow or failed response?", 150,
                "Debounce and a minimum number of characters; cache earlier results; cancel or ignore out-of-date responses; "
                        + "loading and \"no results\" states; keyboard navigation and accessibility; a sensible message on errors; "
                        + "search on the server with an index."));
        q.add(choice("ARCHITECTURE", "After a user updates their profile, your React app still shows the old details. What is the most "
                + "likely cause?", List.of("The CSS is cached", "The app keeps the earlier API response in state or a cache and did "
                + "not update or refetch it after saving", "Java is slower than JavaScript", "The database deleted the update"), 1));
        q.add(aiTools("AI tools can write code quickly. What do you think still makes a good engineer valuable, and how do you plan "
                + "to use AI tools during this internship?"));
        q.add(video("SOFT_SKILLS", "Tell us about a time you were stuck on a problem for hours. How did you get unstuck, and when do "
                + "you decide to ask for help?", 90,
                "A systematic approach (break it down, read errors and docs, isolate, try a minimal example); asks for help "
                        + "after a reasonable effort with a clear question; learns from it."));
        readiness(q);
        return q;
    }

    // ---- shared ----

    private static DraftQuestion intro() {
        return video("INTRO", "In one minute: your programme and year, the technologies you are strongest in, and the kind of "
                + "software you want to build.", 60,
                "Clear and confident; specific technologies, not a long list; real interest in front-end and Java work.");
    }

    private static DraftQuestion aiTools(String prompt) {
        return video("SOFT_SKILLS", prompt, 90,
                "Uses AI tools for real (boilerplate, explanations, debugging ideas, tests) but reads, runs and tests what they "
                        + "get; a concrete example; spots wrong or insecure output; can explain any code they submit. Pasting "
                        + "without understanding, or never trying the tools, are both weak.");
    }

    private static void readiness(List<DraftQuestion> q) {
        q.add(written("Which programme and year are you in (for example MCA, 1st year), and when do you graduate?"));
        q.add(written("The internship is full time and on-site in Bangalore, from December 2026 to May 2027. Are you available for "
                + "the whole period? Mention any exams or college commitments."));
        q.add(written("The stipend is ₹35,000 a month. Strong interns get a full-time offer of ₹8.0 lakhs a year (including 15% "
                + "variable) after the internship. Is this acceptable to you?"));
    }

    private static DraftQuestion video(String phase, String prompt, int seconds, String good) {
        return new DraftQuestion(phase, "VIDEO", prompt, "", List.of(), -1, List.of(), seconds, good);
    }

    private static DraftQuestion writtenCode(String prompt, String good) {
        return new DraftQuestion("TECHNICAL", "WRITTEN", prompt, "", List.of(), -1, List.of(), 0, good);
    }

    private static DraftQuestion written(String prompt) {
        return new DraftQuestion("READINESS", "WRITTEN", prompt, "", List.of(), -1, List.of(), 0, "");
    }

    private static DraftQuestion blank(String prompt, String... accepted) {
        return new DraftQuestion("SKILLS", "FILL_BLANK", prompt, "", List.of(), -1, List.of(accepted), 0, "");
    }

    private static DraftQuestion choice(String prompt, List<String> options, int correct) {
        return choice("TECHNICAL", prompt, options, correct);
    }

    private static DraftQuestion choice(String phase, String prompt, List<String> options, int correct) {
        return new DraftQuestion(phase, "MULTIPLE_CHOICE", prompt, "", options, correct, List.of(), 0, "");
    }

    private static DraftQuestion choiceWithCode(String prompt, String code, List<String> options, int correct) {
        return new DraftQuestion("TECHNICAL", "MULTIPLE_CHOICE", prompt, code, options, correct, List.of(), 0, "");
    }
}
