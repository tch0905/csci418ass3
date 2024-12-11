default: build

build: 
	javac *.java

clean:
	rm -f *.class
	rm -rf data
	rm -rf metadata
	rm -rf tmp

put:
	java MyDedup upload 2 4 8 ../dataset/test_dup1
	java MyDedup upload 2 4 8 ../dataset/test_dup2
	java MyDedup upload 2 4 8 ../dataset/test_dup3
	java MyDedup upload 2 4 8 ../dataset/test_dup4

download:
	java MyDedup download ../dataset/test_dup2 ./tmp/test_dup2