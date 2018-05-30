#!/usr/bin/perl

use warnings;
use strict;
use File::Find;

my $usage = "Usage: $0 CONFIG_FILE ROOT_PATH...\n";

my $config_file = shift or die $usage;
my $root = shift or die $usage;

# Read configuration file

open my $CONFIG, '<', $config_file or die "Config file not found";

my %config;
my %last_found_occurrence_file;

while(<$CONFIG>) {
    chomp;

    # Skip empty lines
    next if(/^$/);

    # Skip comment
    next if(/^#/);

    my @array = split("/===/");

    if($#array > 1) {
        $config{$array[0]} = -1 * $array[1];
    } else {
        $config{$array[0]} = 0;
    }
}

while ($root) {
    find( \&analyseFile, $root );

    $root = shift;
}

my $error = 0;

foreach (keys(%config)) {
    if($config{$_} > 0) {
        print "Error: '" . $_ . "' detected " . $config{$_} . " time(s), last in file '" . $last_found_occurrence_file{$_} . "'.\n";
        $error++;
    }
}

if($error > 0) {
    print STDERR $error . " forbidden pattern(s) detected\n";
    exit 1;
} else {
    print STDERR "All clear, commit allowed!\n";
    exit 0;
}

# Subs

sub analyseFile {
    my $file = $_;

    open my $INPUT, '<', $file or do {
        warn qq|WARNING: Could not open $File::Find::name\n|;
        return;
    };

    while ( <$INPUT> ) {
        my $line = $_;

        foreach (keys(%config)) {
            if ($line =~ /$_/) {
                $config{$_}++;
                $last_found_occurrence_file{$_} = $File::Find::name;
            }
        }
    }
}
