default: build

build: 
	javac *.java

clean:
	rm -f *.class
	rm -rf data
	rm -rf metadata
	rm -rf tmp

put:
	java MyDedup upload 64 1024 2048 ../dataset/test_dup1
	java MyDedup upload 64 1024 2048 ../dataset/test_dup2
	java MyDedup upload 64 1024 2048 ../dataset/test_dup3
	java MyDedup upload 64 1024 2048 ../dataset/test_dup4

putlarge:
	java MyDedup upload 64 1024 2048 ../dataset/test_dup5
	java MyDedup upload 64 1024 2048 ../dataset/test_dup6
	java MyDedup upload 64 1024 2048 ../dataset/test_dup7
	java MyDedup upload 64 1024 2048 ../dataset/test_dup8
	java MyDedup upload 64 1024 2048 ../dataset/test_dup9

putverylarge:
	java MyDedup upload 128 256 512 ../dataset/KJV12.TXT

download:
	java MyDedup download ../dataset/test_dup2 ./tmp/test_dup2

downloadlarge:
	java MyDedup download ../dataset/test_dup5 ./tmp/test_dup5
	java MyDedup download ../dataset/test_dup6 ./tmp/test_dup6
	java MyDedup download ../dataset/test_dup7 ./tmp/test_dup7
	java MyDedup download ../dataset/test_dup8 ./tmp/test_dup8
	java MyDedup download ../dataset/test_dup9 ./tmp/test_dup9

downloadverylarge:
	java MyDedup download ../dataset/KJV12.TXT ./tmp/KJV12.TXT

check: 
	./rfp_anchor_linux64 2048 4096 8192 257 0 ../dataset/KJV12.TXT >> ./tmp/check.txt

checklarge: 
	diff ../dataset/test_dup5 ./tmp/test_dup5
	diff ../dataset/test_dup6 ./tmp/test_dup6
	diff ../dataset/test_dup7 ./tmp/test_dup7
	diff ../dataset/test_dup8 ./tmp/test_dup8
	diff ../dataset/test_dup9 ./tmp/test_dup9
	./rfp_anchor_linux64 64 1024 2048 257 0 ../dataset/test_dup5 >> ./tmp/check_dup5.txt
	./rfp_anchor_linux64 64 1024 2048 257 0 ../dataset/test_dup6 >> ./tmp/check_dup6.txt
	./rfp_anchor_linux64 64 1024 2048 257 0 ../dataset/test_dup7 >> ./tmp/check_dup7.txt
	./rfp_anchor_linux64 64 1024 2048 257 0 ../dataset/test_dup8 >> ./tmp/check_dup8.txt
	./rfp_anchor_linux64 64 1024 2048 257 0 ../dataset/test_dup9 >> ./tmp/check_dup9.txt
	./rfp_anchor_linux64 64 1024 2048 257 0 ../dataset/test_dup5 >> ./data/check_dup5.txt

