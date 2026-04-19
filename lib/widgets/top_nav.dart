import 'package:flutter/material.dart';

class TopNav extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return AppBar(
      backgroundColor: Colors.transparent,
      elevation: 0, // Removes the border
      title: Text('Main Menu', style: TextStyle(color: Colors.black)),
      centerTitle: true,
    );
  }
}
