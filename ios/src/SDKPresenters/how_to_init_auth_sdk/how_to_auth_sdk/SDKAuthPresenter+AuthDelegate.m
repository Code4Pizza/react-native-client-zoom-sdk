//
//  SDKAuthPresenter+authDelegate.m
//  MobileRTCSample
//
//  Created by Zoom Video Communications on 2018/11/21.
//  Copyright © 2018 Zoom Video Communications, Inc. All rights reserved.
//
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import "SDKAuthPresenter+AuthDelegate.h"
#import "RNMeetingCenter.h"

@implementation SDKAuthPresenter (AuthDelegate)

- (void)onMobileRTCAuthReturn:(MobileRTCAuthError)returnValue
{
    NSLog(@"onMobileRTCAuthReturn %d", returnValue);
    
    if (returnValue != MobileRTCAuthError_Success)
    {
//        NSString *message = [NSString stringWithFormat:NSLocalizedString(@"SDK authentication failed, error code: %zd", @""), returnValue];
//        UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"" message:message delegate:self cancelButtonTitle:NSLocalizedString(@"OK", @"") otherButtonTitles:NSLocalizedString(@"Retry", @""), nil];
//        [alert show];
    }
    else {
        // Phunv: Phan nay khoi tao xong Service, kiem tra de join pending no
        if ([[RNMeetingCenter shared] isEnableRNMeetingView]) {
            [[RNMeetingCenter shared] checkPendingJoinMeetingAfterAuth];
        }
    }
    if ([RNMeetingCenter shared].initSDKCallback) {
        [RNMeetingCenter shared].initSDKCallback(@[@(returnValue == MobileRTCAuthError_Success)]);
    }
}

- (void)onMobileRTCLoginReturn:(NSInteger)returnValue
{
    NSLog(@"onMobileRTCLoginReturn result=%zd", returnValue);
    
    MobileRTCPremeetingService *service = [[MobileRTC sharedRTC] getPreMeetingService];
    if (service)
    {
        service.delegate = self;
    }
}

- (void)onMobileRTCLogoutReturn:(NSInteger)returnValue
{
    NSLog(@"onMobileRTCLogoutReturn result=%zd", returnValue);
}

@end
