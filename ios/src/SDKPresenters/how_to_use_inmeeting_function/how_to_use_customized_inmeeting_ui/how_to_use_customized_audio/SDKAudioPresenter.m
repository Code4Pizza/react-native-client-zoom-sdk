//
//  SDKAudioPresenter.m
//  MobileRTCSample
//
//  Created by Zoom Video Communications on 2018/11/20.
//  Copyright © 2018 Zoom Video Communications, Inc. All rights reserved.
//

#import "SDKAudioPresenter.h"
#import "RNZoomAppDelegate.h"

@implementation SDKAudioPresenter

- (void)turnOnAudio {
    MobileRTCMeetingService *ms = [[MobileRTC sharedRTC] getMeetingService];
    if (ms)
    {
        MobileRTCAudioType audioType = [ms myAudioType];
        switch (audioType)
        {
            case MobileRTCAudioType_VoIP: //voip
            case MobileRTCAudioType_Telephony: //phone
            {
                if (![ms canUnmuteMyAudio])
                {
                    break;
                }
                BOOL isMuted = [ms isMyAudioMuted];
                if (isMuted) {
                    [ms muteMyAudio:NO];
                }
                break;
            }
            case MobileRTCAudioType_None:
            {
                if ([ms isSupportedVOIP])
                {
                    if (SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(@"8"))
                    {
                        // Phunv: Comment + add code
                        MobileRTCMeetingService *ms = [[MobileRTC sharedRTC] getMeetingService];
                        if (ms)
                        {
                            [ms connectMyAudio:YES];
                        }
                    }
                }
                break;
            }
        }
    }
}

- (void)turnOffAudio {
    MobileRTCMeetingService *ms = [[MobileRTC sharedRTC] getMeetingService];
    if (ms)
    {
        MobileRTCAudioType audioType = [ms myAudioType];
        switch (audioType)
        {
            case MobileRTCAudioType_VoIP: //voip
            case MobileRTCAudioType_Telephony: //phone
            {
                if (![ms canUnmuteMyAudio])
                {
                    break;
                }
                BOOL isMuted = [ms isMyAudioMuted];
                if (!isMuted) {
                    [ms muteMyAudio:YES];
                }
                break;
            }
            case MobileRTCAudioType_None:
            {
                break;
            }
        }
    }
}

- (void)muteMyAudio
{
    MobileRTCMeetingService *ms = [[MobileRTC sharedRTC] getMeetingService];
    if (ms)
    {
        MobileRTCAudioType audioType = [ms myAudioType];
        switch (audioType)
        {
            case MobileRTCAudioType_VoIP: //voip
            case MobileRTCAudioType_Telephony: //phone
            {
                if (![ms canUnmuteMyAudio])
                {
                    break;
                }
                BOOL isMuted = [ms isMyAudioMuted];
                [ms muteMyAudio:!isMuted];
                break;
            }
            case MobileRTCAudioType_None:
            {
                //Supported VOIP
                if ([ms isSupportedVOIP])
                {
                    if (SYSTEM_VERSION_GREATER_THAN_OR_EQUAL_TO(@"8"))
                    {
                        // Phunv: Comment + add code
                        MobileRTCMeetingService *ms = [[MobileRTC sharedRTC] getMeetingService];
                        if (ms)
                        {
                            [ms connectMyAudio:YES];
                        }
                    }
                }
                break;
            }
        }
    }
}

- (void)dialIn
{
    NSLog(@"MeetingNumber=%@",[[MobileRTCInviteHelper sharedInstance] ongoingMeetingNumber]);
    NSLog(@"participantID=%@",@([[[MobileRTC sharedRTC] getMeetingService] getParticipantID]));
    
    MobileRTCCallCountryCode *currentCountryCode = [[[MobileRTC sharedRTC] getMeetingService] getDialInCurrentCountryCode];
    
    NSArray *countryCodes = [[[MobileRTC sharedRTC] getMeetingService] getDialInCallCodesWithCountryId:currentCountryCode.countryId];

    UIAlertController *alertController = [UIAlertController alertControllerWithTitle:currentCountryCode.countryName
                                                                             message:nil
                                                                      preferredStyle:UIAlertControllerStyleAlert];
    
    for (MobileRTCCallCountryCode *countrgCode in countryCodes) {
        
        NSString *description = countrgCode.tollFree?[NSString stringWithFormat:@" (%@)", NSLocalizedString(@"Toll Free", @"")]:@"";
        NSString *displayCountryNumber = [NSString stringWithFormat:@"%@%@", countrgCode.countryNumber, description];
        
        [alertController addAction:[UIAlertAction actionWithTitle:displayCountryNumber style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
             [[[MobileRTC sharedRTC] getMeetingService] dialInCall:countrgCode.countryNumber];
        }]];
    }
    
    [alertController addAction:[UIAlertAction actionWithTitle:NSLocalizedString(@"Cancel", nil) style:UIAlertActionStyleCancel handler:^(UIAlertAction *action) {
    }]];
    
    RNZoomAppDelegate *appDelegate = (RNZoomAppDelegate *)[UIApplication sharedApplication].delegate;
    [[appDelegate topViewController] presentViewController:alertController animated:YES completion:nil];
    
}

- (void)switchMyAudioSource
{
    MobileRTCAudioError error = [[[MobileRTC sharedRTC] getMeetingService] switchMyAudioSource];
    NSLog(@"Switch My Audio error:%d...", error);
}

- (MobileRTCAudioType)myAudioType
{
    MobileRTCMeetingService *ms = [[MobileRTC sharedRTC] getMeetingService];
    if (ms) {
        MobileRTCAudioType audioType = [ms myAudioType];
        return audioType;
    }
    return MobileRTCAudioType_None;
}

- (BOOL)connectMyAudio:(BOOL)on;
{
    return [[[MobileRTC sharedRTC] getMeetingService] connectMyAudio:on];
}

- (BOOL)muteUserAudio:(BOOL)mute withUID:(NSUInteger)userID
{
    return [[[MobileRTC sharedRTC] getMeetingService] muteUserAudio:mute withUID:userID];
}

- (BOOL)muteAllUserAudio:(BOOL)allowSelfUnmute
{
    return [[[MobileRTC sharedRTC] getMeetingService] muteAllUserAudio:allowSelfUnmute];
}

@end
