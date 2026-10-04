st = []
for t in input().split():
    if t in ("+", "-", "*"):
        b = st.pop(); a = st.pop()
        st.append(a + b if t == "+" else a - b if t == "-" else a * b)
    else:
        st.append(int(t))
print(st[-1])
