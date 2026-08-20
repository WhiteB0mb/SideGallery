with open('app/src/main/java/com/example/MainActivity.kt') as f: lines = f.readlines()
stack = []
for i, l in enumerate(lines):
    if i < 1180: continue
    if i > 1735: break
    for c in l:
        if c == '{': stack.append(i+1)
        elif c == '}':
            if stack: stack.pop()
    print(f"{i+1:4d} {len(stack):2d}: {l.strip()}")
