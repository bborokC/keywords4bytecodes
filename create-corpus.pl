#!/usr/bin/perl -w

# Copyright (c) 2012 Pablo Ariel Duboue <pablo.duboue@gmail.com>

# Permission is hereby granted, free of charge, to any person obtaining
# a copy of this software and associated documentation files (the
# "Software"), to deal in the Software without restriction, including
# without limitation the rights to use, copy, modify, merge, publish,
# distribute, sublicense, and/or sell copies of the Software, and to
# permit persons to whom the Software is furnished to do so, subject to
# the following conditions:

# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.

# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
# NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
# LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
# OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
# WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

# The following code assumes a local debian mirror on ./debian-mirror


use strict;

my$extract_comments = "java -cp ./workspace/net.duboue.reveng.javadoc/bin:/usr/share/java/qdox.jar net.duboue.reveng.javadoc.DumpComments";
my$dump_class = "jclassinfo --disasm";
my$TMP="/tmp/java_reveng";
my$DEBUG=0;

my%from_src=();

# this code goes like this from bash shell, it should be integrated into the code later or subsumed by a different approach
# apt-file search --package-only .jar > packages-with-jar
# for i in `cat packages-with-jars`; do dpkg-query -p $i | perl -ne 'chomp; ($k,$v)=m/^([^:]+): (.*)$/; if($k eq "Source"){print "\t$v"};if($k eq "Filename"){print "\t$v\n"}' >> packages.tsv; done
# cat packages.tsv | ./create-corpus.pl
while(<STDIN>){
    chomp;
    my@parts=split(/\t/,$_);
    my($pkg,$src_pkg,$deb);
    $pkg=shift@parts;
    $src_pkg= @parts==2 ? shift@parts : $pkg;
    $deb=shift@parts;
    if(! $from_src{$src_pkg}){
	$from_src{$src_pkg} = [];
    }
    push@{$from_src{$src_pkg}}, $deb;
}

foreach my$src_pkg(keys %from_src){
    print STDERR "Processing $src_pkg\n";
    # find correct version
    my$version="";
    my$path;
    foreach my$deb(@{$from_src{$src_pkg}}){
	next if($version=~m/[0-9]/);
	if(! $path){
	    $path=$deb;
	    $path=~s/[^\/]+$//;
	    $path="./debian-mirror/$path/";
	}
	$version=$deb;
	$version=~s/_(amd|all).*$//;
	$version=~s/^.*_//;
    }
    my$dsc;
    if($version){
	$dsc=$path.$src_pkg."_$version.dsc";
    }else{
	my$dsc=`find $path -name \*.dsc|head -1`;
    }
    

    # extract src
    `rm -Rf $TMP/src`;
    `dpkg-source -x $dsc $TMP/src`;

    # expand all .zip, tar.gz, tar.bz2 on the root of src
    foreach my $tag(split(/\n/, `find $TMP/src -name '*.zip'`)){
	`cd $TMP/src; unzip $tag`;
    }
    foreach my $tag(split(/\n/, `find $TMP/src -name '*.tar.gz'`)){
	`cd $TMP/src; tar -zxf $tag`;
    }
    foreach my $tag(split(/\n/, `find $TMP/src -name '*.tar.bz2'`)){
	`cd $TMP/src; tar -zjf $tag`;
    }
	
    # get .java
    my@all_java=`find $TMP/src -name \*.java`;
    chomp(@all_java);
    print STDERR "Found ".scalar(@all_java)." Java source files.\n";

    my@comments=();
    while(@all_java){
	my@to_process=@all_java > 500 ? splice(@all_java, -500) : splice(@all_java, 0);
	my$all_java = join(" ",map { "\"$_\"" } @to_process);
	my$comments=`$extract_comments $all_java`;
	push@comments, split(/\n/,$comments);
    }

    # get comments
    my%signature_to_comment=();
    foreach my$comment(@comments){
	my@parts=split(/\t/,$comment);
	my($class,$short,$long,$comment)=@parts;
	next unless($comment);
	my$full_class=$class;
	my$full_long=$long;
	$class=~s/^.*\.//;
	my($methodname,$params)=split(/\(/,$long);
	$params=~s/ throws.*$//;
	$params=~s/ [^, ]+,/,/g;
	$params=~s/ [^) ]+\)/)/g;
	my@params=split(/, /,$params);
	@params=map{s/^.*\.//; $_}@params;
	@parts=split(/ /,$methodname);
	@parts=map{s/^.*\.//; s/^.*\$// if(m/\$/); $_ } @parts;
	@parts=grep(!/(public)|(private)|(protected)|(abstract)|(final)|(static)|(synchronized)/,@parts);
	$methodname=join(" ",@parts);
	$long="$methodname(".join(", ", @params);
	$comment=~s/\s+/ /g;
	my$signature="$class $long";
	print STDERR "($full_class) $full_long ===> '$signature'\n" if($DEBUG);
	$signature_to_comment{$signature}={ comment=>$comment, 
					    full_class=>$full_class, 
					    long=>$full_long };
    }
    print STDERR "Found ".scalar(keys %signature_to_comment)." commented methods.\n";

    foreach my$deb(@{$from_src{$src_pkg}}){
	print STDERR "\tProcessing $deb\n";
	`rm -Rf $TMP/deb`;
	`dpkg -x ./debian-mirror/$deb $TMP/deb`;
	# find jars
	my$jars=`find $TMP/deb -name \*.jar`;
	my@jars=split(/\n/,$jars);

	# extract jars
	`rm -Rf $TMP/jar`;
	`mkdir $TMP/jar`;
	foreach my$jar(@jars){
	    `cd $TMP/jar; jar xf $jar`;
	}

	# for each class, disassemble
	my$classes=`find $TMP/jar -name \*.class`;
	my@classes=split(/\n/,$classes);
	print STDERR "\tFound ".scalar(@classes)." bytecode files.\n";
	my$print_count=0;
	foreach my$class(@classes){
	    my$disasm=`$dump_class $class`;
	    my@disasm=split(/\n/,$disasm);
	    shift@disasm; # [METHODS]
	    my$printing=0;
	    my$short_class = $class;
	    $short_class=~s/^.*\///;
	    $short_class=~s/\.class//;
	    foreach my$line(@disasm){
		if($line=~m/^[^\s\{]/ && !($line eq "}")){ 
		    # match method to comment
		    my$is_interface = $line !~ m/\{/;
		    $line=~s/ ?\{// unless($is_interface);
		    $line=~s/ throws.*$//;
		    my($methodname,$params)=split(/\(/,$line);
		    if($params){
			my@parts=split(/ /,$methodname);
			@parts=map{s/^.*\.//; s/^.*\$// if(m/\$/); $_} @parts;
			@parts=grep(!/(public)|(private)|(protected)|(abstract)|(final)|(static)|(synchronized)/,@parts);

			$methodname=join(" ",@parts);

			#$params=~s/ [^, ]+,/,/g;
			#$params=~s/ [^) ]+\)/)/g;
			my@params=split(/, /,$params);
			@params=map{s/^.*\.//; s/^.*\$// if(m/\$/); $_} @params;
			$line="$methodname(".join(", ", @params);
		    }else{
			my@parts=split(/ /,$line);
			@parts=map{s/^.*\.//; s/^.*\$// if(m/\$/); $_} @parts;
			@parts=grep(!/(public)|(private)|(protected)|(abstract)|(final)|(static)|(synchronized)/,@parts);
			$line=join(" ",@parts);
		    }
		    $line=~s/\s+$//;
		    my$signature = "$short_class $line";
		    print STDERR "'$signature'" if($DEBUG);

		    # found? print
		    if($signature_to_comment{$signature}){
			print STDERR " MATCH\n" if($DEBUG);
			my$c=$signature_to_comment{$signature};
			if(defined($c->{found}) && $DEBUG){
			    print STDERR "Duplicated! $signature\n";
			}
			$c->{found} = 1;
			print $c->{full_class}."\t$signature\t".$c->{long}."\t".$c->{comment};
			if($is_interface){
			    print"\n";
			    $print_count++;
			}else{
			    $printing=1;
			}
		    }else{
			print STDERR "\n" if($DEBUG);
		    }
		}elsif($printing){
		    if($line=~m/\}/){
			$printing=0;
			print"\n";
			$print_count++;
		    }else{
			$line=~s/\s+/ /g;
			print"\t$line";
		    }
		}
	    }
	}
	print STDERR "\tFound $print_count matching methods.\n";
	if($DEBUG){
	    print STDERR "Missing methods:\n";
	    foreach my$signature(keys %signature_to_comment){
		my$c=$signature_to_comment{$signature};
		print STDERR "\t$signature\n" unless(defined($c->{found}));
	    }
	}	
    }
}
