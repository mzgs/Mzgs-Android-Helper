# MzgsHelper does not use reflection, JNI, or name-based serialization for its
# own classes, so it needs no additional consumer keep rules.
#
# Ad SDKs and mediation adapters bundle their required rules in their AARs.
# Let the consuming application's R8 merge those rules automatically. Do not
# keep entire SDK packages or com.mzgs.helper: that prevents the application
# from shrinking, optimizing, and obfuscating otherwise reachable code.
#
# If a future helper feature introduces reflection, add a rule scoped to that
# specific class/member here, with a comment explaining the runtime lookup.
