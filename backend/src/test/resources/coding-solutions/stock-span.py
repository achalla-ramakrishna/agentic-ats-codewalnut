import sys
d = sys.stdin.read().split()
n = int(d[0]); a = list(map(int, d[1:1 + n]))
st = []; out = []
for i, p in enumerate(a):
    while st and a[st[-1]] <= p:
        st.pop()
    out.append(i + 1 if not st else i - st[-1])
    st.append(i)
print(" ".join(map(str, out)))
