s = input()
t = [c.lower() for c in s if c.isalnum()]
print("YES" if t == t[::-1] else "NO")
